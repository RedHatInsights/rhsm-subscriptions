import click
import logging

from . import __version__, SwatchContext
from .deploy.command import ee

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

    for key, value in config:
        ctx.obj.set_config(key, value)


cli.add_command(ee)

if __name__ == "__main__":
    cli()
