import logging
import click
import rich.logging
import typing as t

from rich.console import Console
from rich.traceback import install
from invoke.config import Config, merge_dicts

__version__ = "0.1.0"

#    "[%(asctime)s] [%(levelname)s] "
#    "[%(filename)s:%(funcName)s:%(lineno)d] - %(message)s"
LOG_FMT = "%(message)s"
LOG_DATEFMT = "%Y-%m-%d %H:%M:%S"

# Rich setup
console = Console()
error_console = Console(stderr=True)

install(show_locals=False)

log = logging.getLogger(__name__)


def init_log_level(log_level):
    level = getattr(logging, log_level) if log_level else logging.INFO
    logging.basicConfig(
        level=level,
        format=LOG_FMT,
        datefmt="[%X]",
        handlers=[rich.logging.RichHandler()],
    )


def info(msg):
    console.print(f"[green]{msg}[/green]")


def notice(msg):
    console.print(f"[yellow]{msg}[/yellow]")


def err(msg):
    error_console.print(f"[bold red]{msg}[/bold red]")


class SwatchDogInvokeConfig(Config):
    prefix: str = "swatchdog"
    env_prefix: str = "SWATCHDOG"

    @staticmethod
    def global_defaults() -> t.Dict[str, t.Any]:
        their_defaults: t.Dict[str, t.Any] = Config.global_defaults()
        my_defaults: t.Dict[str, t.Any] = {
            "run": {
                "echo": True,
            },
        }
        return merge_dicts(their_defaults, my_defaults)


invoke_config = SwatchDogInvokeConfig()


class SwatchContext:
    def __init__(self, **kwargs):
        self.config: t.Dict[str, t.Any] = dict(**kwargs)

    def __getitem__(self, item):
        return self.config[item]

    def __setitem__(self, key, value):
        self.config[key] = value

    def __contains__(self, item):
        return item in self.config

    def __delitem__(self, key):
        del self.config[key]

    def __len__(self):
        return len(self.config)

    def __iter__(self):
        return iter(self.config)

    def __str__(self):
        return f"SwatchContext: {self.config}"


pass_swatch = click.make_pass_decorator(SwatchContext)


class SwatchDogError(Exception):
    """Errors specific to swatchdog"""

    pass
