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
install(show_locals=False)


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
    console.print(f"[bold red]{msg}[/bold red]")


class SwatchDogInvokeConfig(Config):
    prefix = "swatchdog"
    env_prefix = "SWATCHDOG"

    @staticmethod
    def global_defaults():
        their_defaults = Config.global_defaults()
        my_defaults = {
            "run": {
                "echo": True,
            },
        }
        return merge_dicts(their_defaults, my_defaults)


invoke_config = SwatchDogInvokeConfig()


class SwatchContext:
    def __init__(self, *, log_level: str):
        init_log_level(log_level)
        self.config: t.Dict[str, t.Any] = {}

    def has_config(self, key: str):
        return key in self.config

    def get_config(self, key: str) -> t.Any:
        return self.config[key]

    def set_config(self, key: str, value: t.Any):
        self.config[key] = value
        notice("Config:")
        notice(f"  {key} = {value}")


pass_swatch = click.make_pass_decorator(SwatchContext)


class SwatchDogError(Exception):
    """Errors specific to swatchdog"""

    pass
