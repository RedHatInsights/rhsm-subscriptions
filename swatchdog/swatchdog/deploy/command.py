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
    if ee_token is None:
        ee_token = openshift.whoami("-t")
    if not swatch.has_config("ee_token"):
        swatch.set_config("ee_token", ee_token)
    # Create a simple dictionary for commands in this group to communicate over
    # The SwatchContext will be in the parent context to this one
    ctx.obj = dict()


@ee.command()
@click.option("-c", "--clean", type=bool, default=True, show_default=True)
@pass_swatch
@click.pass_context
def build(ctx, swatch: SwatchContext, clean: bool):
    c = InvokeContext(invoke_config)
    clean_arg = ""
    if clean:
        clean_arg = "clean"

    try:
        result: t.Optional[Result] = c.run("git rev-parse --show-toplevel", hide=True)
    except UnexpectedExit as e:
        console.print_exception()
        err("Could not determine project root")
        sys.exit(e.result.exited)

    project_root: str = result.stdout.rstrip()
    # version_scrapper = GradleWatcher(pattern = r"Inferred project:.*")

    with c.cd(project_root):
        info("Running build")
        try:
            results: t.List = []
            version_scrapper = GradleWatcher(
                # Use a positive lookbehind to avoid pulling the "Inferred project"
                # into the match
                pattern=r"(?<=Inferred project:).*",
                results=results,
            )
            c.run(f"./gradlew {clean_arg} assemble", watchers=[version_scrapper])
        except UnexpectedExit as e:
            err("Build failed")
            sys.exit(e.result.exited)

    # TODO Handle multiple results in the future
    if len(version_scrapper.results) != 1:
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
@click.pass_context
def deploy(ctx):
    # TODO need to set back to true
    ctx.invoke(build, clean=False)

    artifacts = ctx.obj["artifacts"]
    version = ctx.obj["version"]
    common_prefix = os.path.commonpath(artifacts)

    display_artifacts = collections.OrderedDict()
    for text in sorted(artifacts):
        display_text = os.path.relpath(text).removesuffix(f"{version}.jar")
        display_text += "[...].jar"
        display_artifacts[display_text] = text

    deployable = choose_artifact(display_artifacts)
    deployment_selector = choose_pods()


def choose_pods() -> openshift.Selector:
    pod_choices: t.Dict[str, str] = {}
    for pod in openshift.selector("pods").objects():
        pod_choices[pod.name()] = pod.qname()

    # TODO: Could we be using openshift labels better?  Tagging our pods with a swatch label so we
    # can actually select on it instead of pulling back everything and then filtering?
    selections = iterfzf.iterfzf(pod_choices.keys(), query="swatch", multi=True)
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
