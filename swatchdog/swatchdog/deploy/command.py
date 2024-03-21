import logging
import os

import click
import re
import sys
import iterfzf
import openshift
import typing as t

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
@click.option("--clean/--no-clean", default=True)
@click.option("--pod-prefix", type=str)
@click.option("--project", type=str)
@click.option("--container", type=str)
@click.pass_context
def deploy(ctx, clean: bool, pod_prefix: str, project: str, container: str):
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
    sync_code(rsync_dir, deployment_selector, container)


def build_project(project: str, project_root: str, clean: bool) -> str:
    clean_arg = ""
    if clean:
        clean_arg = "clean"

    c = InvokeContext(invoke_config)

    with c.cd(project_root):
        info("Running build")
        # Compile the class files
        try:
            # The mutable-jar is what allows for the reloading capability in quarkus.
            # The quarkus apps images have to be built as mutable jars for the reloading
            # to work in the first place.  That can be done using the Podman build
            # arg --build-arg=QUARKUS_BUILD_ARGS=-Dquarkus.package.type=mutable-jar
            c.run(
                f"./gradlew {clean_arg} {project}:assemble -Dquarkus.package.type=mutable-jar"
            )
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
            if is_quarkus_project(results[0]):
                rsync_dir = quarkus_rsync_source(results[0])
            else:
                rsync_dir = spring_rsync_source(results[0])
        else:
            raise SwatchDogError(f"Ambiguous build location: {results}")

        if os.path.exists(rsync_dir):
            return rsync_dir
        else:
            raise SwatchDogError(f"No directory {rsync_dir} to sync class files from")


def is_quarkus_project(build_dir: str) -> bool:
    return os.path.exists(os.path.join(build_dir, "quarkus-app"))


def spring_rsync_source(build_dir: str) -> str:
    rsync_dir = os.path.join(build_dir, "classes", "java", "main")
    # This is important.  Without the trailing slash, rsync will sync the
    # directory by name rather than the contents of the directory. I.e. you will
    # end up with "/deployments/main/META-INF" rather than
    # "/deployments/META-INF".  See the rsync man page's USAGE section and
    # look for the phrase "trailing slash".
    return f"{rsync_dir}{os.path.sep}"


def quarkus_rsync_source(build_dir: str) -> str:
    # FIXME: There is a issue with Quarkus hot-deployment.  Because the build artifact
    #  for our code has the git hash in it, if you do a git commit and then rsync, the
    #  rsync places the new JAR right beside the old JAR and the code never redeploys.
    #  Potentially, there is a way around this using rsync's delete functionality,
    #  (since the java command that is invoked actually runs quarkus-run.jar,
    #  but I'm not sure how quarkus-run references our JAR.  If quarkus-run just looks
    #  at whatever is in app/ or whether it has a hard reference.
    #      Another option would be to just do development builds with a static prefix
    #  than one that is based on the date, branch, git ref, etc.  E.g. instead of
    #  swatch-contracts-1.1.0-snapshot.202403201953.uncommitted+awood.swatchdog
    #  .7b6d784.jar, we name the artifact swatch-contracts-1.1.0-dev-snapshot.jar
    #  This approach would likely require some trickery (build-args probably) in the
    #  Dockerfile since we would need to control the artifact name used in the inital
    #  build there.
    rsync_dir = os.path.join(build_dir, "quarkus-app")
    # See comment in spring_rsync_source for while the trailing slash is imperative
    return f"{rsync_dir}{os.path.sep}"


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
        sorted(results_dict.keys()), query=selection, __extra__=["--select-1"]
    )
    return results_dict[choice]


def sync_code(rsync_dir, deployment_selector, container):
    # oc cp/rsync deployable to /deployments*
    # Need to also handle container name selection from the pod

    for pod in deployment_selector.objects():
        if container:
            dest_container = container
        else:
            containers: t.List = [
                p.name
                for p in pod.model.spec.containers
                if "web" in map(lambda x: x["name"], p["ports"])
            ]

            if len(containers) == 1:
                dest_container = containers[0]
            else:
                raise SwatchDogError(
                    f"Could not determine container to deploy to from list {containers}"
                )

        # TODO there's an option for oc rync: -w to watch a directory.  Worth exploring
        #   for an even tighter deployment loop.  The difficulty is with handling clean
        #   operations.  Would definitely want --delete=false on for that
        # NB: if you add non-long form options be sure to use two strings.
        # E.g. ["-x", "something"] and not ["-x something"]
        info(f"Syncing code to {container} in {pod.name()}")
        rsync_args = [
            "--no-perms=true",
            # TODO need to handle deleting a class file properly. Right now
            #  the rsync delete will trash everything else under /deployments (e.g.
            #  "/lib").  We'd need to do an rsync for each directory under "main".  Not
            #  too hard but we'll need to remove the os.path.sep from the source
            #  argument since we'll want the directory itself to appear in
            #  /deployments, not just the contents
            # "--delete=true",  # TODO might want to make this an option people can set
            "--strategy=rsync",
            f"--container={dest_container}",
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
        pod_choices.keys(),
        query=pod_prefix,
        multi=True,
        __extra__=["--header", header, "--select-1"],
    )
    return openshift.selector([pod_choices[x] for x in selections])
