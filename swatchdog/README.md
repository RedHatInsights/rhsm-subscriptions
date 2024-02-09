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

