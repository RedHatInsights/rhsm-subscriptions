# Swatchdog

## Get Started

* Install poetry
  ```shell
  dnf install poetry
  ```
* Create and activate the virtual env
  ```shell
  poetry shell
  ```
* Install dependencies
  ```shell
  poetry install
  ```
* Run swatchdog
  ```shell
  poetry run swatchdog ee deploy
  ```

## `ee`

This subcommand provides functionality dealing with the ephemeral environments.

### `deploy`

Use `deploy` to do a hot-deploy of the Spring code.  (Quarkus support will be in
a later version).

Options:
* `--clean`/`--no-clean`: Whether to run a `gradle clean` as part of the build
  process.  Defaults to `--clean`
* `-p`/`--project`: The gradle project to build and deploy.  Use `:` for the
  root project.
* `-p`/`--pod-prefix`: The pod prefix to deploy to.  E.g. `swatch-api-service`
* `--container`: The container within the pod to deploy to.  Useful because some
  of our pods use sidecars.

## Resolving Conflicts
If you get a merge conflict in `poetry.lock` (generally due to Dependabot
updating a dependency), then do the following:

```shell
git restore --staged --worktree poetry.lock
poetry lock --no-update
```

When rebasing a feature branch on main, this preserves pins from the main
branch, and recomputes pins for your feature branch. You would then follow up
with these commands to continue the rebase:

```shell
git add poetry.lock
git rebase --continue
```

Reprinted from [here](https://github.com/python-poetry/poetry/issues/496#issuecomment-738680177)
