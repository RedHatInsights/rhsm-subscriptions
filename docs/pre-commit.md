# Pre-commit Setup

Install `rh-multi-pre-commit`.  It's on Gitlab under the
`infosec-public/developer-workbench/tools` repo.  The README has information on
installation.

Alternatively, you can just install [pre-commit](https://pre-commit.com/) alone,
but the RH variant has useful tooling to check for credential leaks and to apply
global configuration which I find useful.

If you are using the `rh-multi-pre-commit`, you need to enable local hooks for
the rhsm-subscriptions repo with the command `git config --bool
rh-pre-commit.enableLocalConfig true`.

You can modify the `~/.config/pre-commit/config.yaml` file to add your own
global hooks if you like.

If you don't want to run the hooks for a particular commit, you can use
`--no-verify` when you commit.  If you don't want to run the hooks at all, just
don't configure `pre-commit`.

Lastly, if you don't want colored status messages on the commit hooks, you can
set the env var `PRE_COMMIT_COLOR=never`.
