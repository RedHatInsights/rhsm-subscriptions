import click
import logging
import sys
import typing as t

from . import __version__, SwatchContext, invoke_config, console, err
from .deploy.command import ee

# Trying to avoid some confusion here because otherwise we have invoke.Context
# calls (from the Invoke library we use for shell commands) and context.Invoke() calls
# from Click.

from invoke import Context as InvokeContext
from invoke import UnexpectedExit, Result


log = logging.getLogger(__name__)


@click.group()
@click.option(
    "--config",
    nargs=2,
    multiple=True,
    metavar="KEY VALUE",
    help="Overrides a config key/value pair.",
)
@click.option(
    "--verbose", "-v", is_flag=True, default=False, help="Enables verbose mode."
)
@click.version_option(version=__version__)
@click.pass_context
def cli(ctx, config, verbose):
    # A note about how context, context.object, and SwatchContext are all related: Every
    # command call has a context (with Click related stuff) and every context has an
    # "object" (which can be of an arbitrary type and is used for passing around state
    # data.  When a command calls another command, another context is created and the
    # two are chained together.  The SwatchContext object is meant to be at the
    # top-level context since it contains basic configuration data.  The @pass_swatch
    # decorator is a decorator that walks up the context chain and will return the first
    # SwatchContext object it finds.  The pass_object and pass_context decorators mark
    # the method as wanting to receive the current context (or object).

    # See https://click.palletsprojects.com/en/8.1.x/complex/#contexts and
    # https://click.palletsprojects.com/en/8.1.x/complex/#interleaved-commands
    if verbose:
        level = "DEBUG"
    else:
        level = "INFO"

    ctx.obj = SwatchContext(log_level=level)
    log.debug("Verbose logging is enabled.")

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

    for key, value in config:
        ctx.obj.set_config(key, value)


cli.add_command(ee)

if __name__ == "__main__":
    cli()
