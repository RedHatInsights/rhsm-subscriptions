import argparse
import logging
import os
import shlex
import subprocess

logging.basicConfig()
log = logging.getLogger("init-application")


class ActionHandler:
    def __init__(self, **kwargs):
        self.args = argparse.Namespace(kwargs)

    def shell_cmd(self, command):
        try:
            log.debug("Running '%s'" % command)
            completed_process = subprocess.run(
                shlex.split(command),
                check=True,
                stderr=subprocess.PIPE,
                stdout=subprocess.PIPE
            )
            if completed_process.stdout:
                log.debug(completed_process.stdout.decode("utf-8"))
            return completed_process
        except subprocess.CalledProcessError as e:
            raise RuntimeError("Process execution failed: %s" % e.stderr.decode("utf-8")) from e

    def run(self, *args, **kwargs):
        raise NotImplementedError("Subclasses must define this method")


class SubmoduleHandler(ActionHandler):
    def __init__(self):
        pass

    def run(self, *args, **kwargs):
        command = "git submodule update --init --recursive"
        self.shell_cmd(command)


class PostgresDbHandler(ActionHandler):
    def __init__(self, **kwargs):
        self.db_host = kwargs.setdefault("db_host", "localhost")
        self.db_user = kwargs.setdefault("db_user", "")
        self.db_password = kwargs.setdefault("db_password", "")
        self.db_name = kwargs.setdefault("db_name", "")
        self.auth()

    def connect_options(self):
        opts_dict = {
            "host": self.db_host,
            "username": self.db_user,
        }
        opts = ["--%s=%s" % (flg, arg) for flg, arg in opts_dict.items() if arg is not None]
        return " ".join(opts)

    def auth(self):
        if self.db_password:
            os.environ['PGPASSWORD'] = self.db_password

    def db_exists(self):
        command = "psql -tAq %s -c \"select 1 from pg_database where datname='%s'\" postgres" %\
              (self.connect_options(), self.db_name)
        completed_process = self.shell_cmd(command)
        output = completed_process.stdout.decode("utf-8")
        exists = [True for l in output.splitlines() if l.strip() == "1"]
        return any(exists)

    def run(self, *args, **kwargs):
        if self.db_exists():
            log.info("%s database already exists" % self.db_name)
            return

        command = "sudo createdb %s %s" % (self.connect_options(), self.db_name)
        self.shell_cmd(command)

