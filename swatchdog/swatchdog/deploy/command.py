import logging
import os

import click
import collections
import re
import sys
import glob
import openshift
import typing as t

from rich.columns import Columns
from rich.text import Text

import iterfzf
from prompt_toolkit import prompt
from prompt_toolkit.completion import Completer, Completion, FuzzyCompleter

from .. import SwatchContext, SwatchDogError
from .. import console, info, err, invoke_config, pass_swatch

# Trying to avoid some confusion here because otherwise we have invoke.Context
# calls (from the Invoke library we use for shell commands) and context.Invoke() calls
# from Click.

from invoke import Context as InvokeContext
from invoke import UnexpectedExit, StreamWatcher, Result


class GradleWatcher(StreamWatcher):
    def __init__(self, pattern: str, results: t.List) -> None:
        r"""
        Imprint this `Responder` with necessary parameters.

        :param pattern:
            A raw string (e.g. ``r"\[sudo\] password for .*:"``) which will be
            turned into a regular expression.

        :param results:
            A list to append matching lines to.  Would prefer just using an attribute
            on the class instance, when I do that, the instance only ever has an
            empty list (even when items are appended successfully during the submit
            method).
        """
        # TODO: precompile the keys into regex objects
        super(GradleWatcher, self).__init__()
        self.pattern = pattern
        self.results = results
        self.index = 0

    def pattern_matches(
        self, stream: str, pattern: str, index_attr: str
    ) -> t.Iterable[str]:
        """
        Generic "search for pattern in stream, using index" behavior.

        :param str stream: The same data passed to ``submit``.
        :param str pattern: The pattern to search for.
        :param str index_attr: The name of the index attribute to use.
        :returns: An iterable of string matches.

        """
        # NOTE: generifies scanning so it can be used to scan for >1 pattern at
        # once, e.g. in FailingResponder.
        # Only look at stream contents we haven't seen yet, to avoid dupes.
        index = getattr(self, index_attr)
        new = stream[index:]
        # Search, across lines if necessary
        matches = re.findall(pattern, new, re.S)
        # Update seek index if we've matched
        if matches:
            setattr(self, index_attr, index + len(new))
        return matches

    def submit(self, stream: str) -> t.Iterable[str]:
        # Iterate over findall() response in case >1 match occurred.
        for match in self.pattern_matches(stream, self.pattern, "index"):
            self.results.append(match)
        return []


@click.group
@click.option("--ee-token", envvar="OCP_CONSOLE_TOKEN", type=str)
@pass_swatch
@click.pass_context
def ee(ctx, swatch: SwatchContext, ee_token: str):
    ctx.obj = dict()
    if ee_token is None:
        ee_token = openshift.whoami("-t")
    if not swatch.has_config("ee_token"):
        swatch.set_config("ee_token", ee_token)
    # Create a simple dictionary for commands in this group to communicate over
    # The SwatchContext will be in the parent context to this one
    c = InvokeContext(invoke_config)
    try:
        result: t.Optional[Result] = c.run("git rev-parse --show-toplevel", hide=True)
    except UnexpectedExit as e:
        console.print_exception()
        err("Could not determine project root")
        sys.exit(e.result.exited)

    project_root: str = result.stdout.rstrip()
    ctx.obj["project_root"] = project_root


@ee.command()
# TODO allow passage of a gradle coordinate to build a specific project and pass that
#   down to the selection dialog in deploy so only the artifacts built are shown
@click.option("-c", "--clean", type=bool, default=True, show_default=True)
@pass_swatch
@click.pass_context
def build(ctx, swatch: SwatchContext, clean: bool):
    c = InvokeContext(invoke_config)
    project_root: str = ctx["project_root"]
    clean_arg = ""
    if clean:
        clean_arg = "clean"

    with c.cd(project_root):
        info("Running build")
        try:
            results: t.List = []
            version_scraper = GradleWatcher(
                # Use a positive lookbehind to avoid pulling the "Inferred project"
                # into the match
                pattern=r"(?<=Inferred project:).*",
                results=results,
            )
            c.run(f"./gradlew {clean_arg} assemble", watchers=[version_scraper])
        except UnexpectedExit as e:
            err("Build failed")
            sys.exit(e.result.exited)

    # TODO Handle multiple results in the future
    if len(version_scraper.results) != 1:
        raise SwatchDogError("Build did not result in a single version")
    else:
        version = results[0]
        match = re.search(r".*, version:\s+(?P<version>\S+)", version)
        ctx.obj["artifacts"] = find_artifacts(project_root, match.group("version"))
        ctx.obj["version"] = match.group("version")


def find_artifacts(project_root, version) -> t.List:
    search_glob = f"{project_root}{os.sep}**{os.sep}*{version}.jar"
    info(f"Searching for {search_glob}")
    return glob.glob(search_glob, root_dir=project_root, recursive=True)


@ee.command()
@click.option("--clean/--no-clean", default=True)
@click.option("-p", "--pod-prefix", type=str)
@click.option("--project", type=str)
@click.pass_context
def deploy(ctx, clean: bool, pod_prefix: str, project: str):
    try:
        openshift.whoami()
    except Exception as e:
        logging.exception(e)
        err("Could not communicate with Openshift. Are you logged in?")
        sys.exit(1)

    project_root = ctx.obj["project_root"]
    project: str = choose_project(project_root, selection=project)
    rsync_dir: str = build_project(project, project_root, clean)
    deployment_selector: openshift.Selector = choose_pods(pod_prefix)
    sync_code(rsync_dir, deployment_selector)


def build_project(project: str, project_root: str, clean: bool) -> str:
    clean_arg = ""
    if clean:
        clean_arg = "clean"

    c = InvokeContext(invoke_config)

    with c.cd(project_root):
        info("Running build")
        # Compile the class files
        try:
            c.run(f"./gradlew {clean_arg} {project}:classes")
        except UnexpectedExit as e:
            err("Build failed")
            sys.exit(e.result.exited)

        # Determine where those class files were placed
        try:
            results: t.List = []
            build_dir_scraper = GradleWatcher(
                pattern=r"(?<=buildDir: )\S+", results=results
            )
            c.run(
                f"./gradlew {project}:properties --property buildDir",
                watchers=[build_dir_scraper],
            )
        except UnexpectedExit as e:
            err("Could not determine build directory")
            sys.exit(e.result.exited)

        # Get the path to the directory to rsync to the pod
        if len(results) == 1:
            rsync_dir = os.path.join(results[0], "classes", "java", "main")
            # This is important.  Without the trailing slash, rsync will sync the
            # directory by name rather than the contents of the directory. I.e. you will
            # end up with "/deployments/main/META-INF" rather than
            # "/deployments/META-INF".  See the rsync man page's USAGE section and
            # look for the phrase "trailing slash".
            rsync_dir = f"{rsync_dir}{os.path.sep}"
        else:
            raise SwatchDogError(f"Ambiguous build location: {results}")

        if os.path.exists(rsync_dir):
            return rsync_dir
        else:
            raise SwatchDogError(f"No directory {rsync_dir} to sync class files from")


def choose_project(project_root: str, selection: str) -> str:
    c = InvokeContext(invoke_config)
    with c.cd(project_root):
        try:
            results: t.List = []
            project_scraper = GradleWatcher(
                # Use a positive lookbehind to avoid pulling the "Project"
                # into the match
                pattern=r"[\|\+\-\\ ]+(?<=Project )'[:\w\-]+'",
                results=results,
            )
            c.run("./gradlew projects", watchers=[project_scraper])
        except UnexpectedExit as e:
            err("Build failed")
            sys.exit(e.result.exited)
    # Add the root project
    results = [r.strip("' ") for r in results]

    results_dict: dict = dict(zip(results, results))
    results_dict[": <root project>"] = ""
    choice: str = iterfzf.iterfzf(
        sorted(results_dict.keys()),
        query=selection,
        # TODO get select-1 working
        # __extra__=["--select-1"]
    )
    return results_dict[choice]


def sync_code(rsync_dir, deployment_selector):
    # oc cp/rsync deployable to /deployments*
    # Need to also handle container name selection from the pod

    for pod in deployment_selector.objects():
        containers: t.List = [
            p.name
            for p in pod.model.spec.containers
            if "web" in map(lambda x: x["name"], p["ports"])
        ]

        if len(containers) == 1:
            container = containers[0]
        else:
            raise SwatchDogError(
                f"Could not determine container to deploy to from list {containers}"
            )

        # TODO there's an option for oc rync: -w to watch a directory.  Worth exploring
        #   for an even tighter deployment loop.  The difficulty is with handling clean
        #   operations.  Would definitely want --delete=false on for that
        # NB: if you add non-long form options be sure to use two strings.
        # E.g. ["-x", "something"] and not ["-x something"]
        rsync_args = [
            "--no-perms=true",
            "--progress=true",
            # TODO need to handle deleting a class file properly. Right now
            #  the rsync delete will trash everything else under /deployments (e.g.
            #  "/lib").  We'd need to do an rsync for each directory under "main".  Not
            #  too hard but we'll need to remove the os.path.sep from the source
            #  argument since we'll want the directory itself to appear in
            #  /deployments, not just the contents
            # "--delete=true",  # TODO might want to make this an option people can set
            "--strategy=rsync",
            f"--container={container}",
            f"{rsync_dir}",
            f"{pod.name()}:/deployments/",
        ]
        result: openshift.Result = openshift.invoke(
            "rsync", rsync_args, auto_raise=True
        )
        print(result.out())


def choose_pods(pod_prefix: str) -> openshift.Selector:
    pod_choices: t.Dict[str, str] = {}
    for pod in openshift.selector("pods").objects():
        pod_choices[pod.name()] = pod.qname()

    # TODO: Could we be using openshift labels better?  Tagging our pods with a swatch label so we
    # can actually select on it instead of pulling back everything and then filtering?
    header: str = "TAB/SHIFT-TAB for multiple selections"
    selections = iterfzf.iterfzf(
        pod_choices.keys(), query=pod_prefix, multi=True, __extra__=["--header", header]
    )
    return openshift.selector([pod_choices[x] for x in selections])


def choose_artifact(display_artifacts) -> str:
    # Extracting this to a method in case we want to change how this selection is
    # performed.  There are several other promising libraries: bullet,
    # simple-term-menu, console-menu but none of them yet offer the unique
    # combination of features that I want.  Namely, a fuzzy searching functionality
    # coupled with arrow key selections.  I feel like those two put together are the
    # fastest way to make a selection, especially on a long list.
    choice = iterfzf.iterfzf(display_artifacts.keys(), query="build/libs/")
    return display_artifacts[choice]
