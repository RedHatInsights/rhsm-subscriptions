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
#### Prerequisites
In order for hot-deploy to work, your containers must be built in a specific
way.  Namely, `-Dquarkus.package.type=mutable-jar` needs to be passed in during
the build and the Gradle `snapshot` task needs to be invoked so you get a
consistent JAR name rather than one that has the git hash in it.  (If the JAR
has the git hash in it, every time you do a git commit, the rsync that copies
your build artifact to the pod would just lay down a JAR with the new name
instead of replacing the running JAR).

The easiest way to meet these requirements is to **use the `bin/build-images.sh`
script**.  This script requires that you have created a user in quay.io and that
all the image repos have been marked as public since Openshift can't pull the
image if it is private.  The repos are marked as private by default so after you
do your first build go to your repos and set "Repository Visibility" to public
under the settings section.

If you don't want to use `bin/build-images.sh`, then you need to make sure that
your image builds incorporate the `--build-arg-file bin/dev-argfile.conf` (or
specify all the arguments in that file directly with `--build-arg`).

The easiest way to make sure your image is built correctly is to simply look at
the built container.  If it has a `/deployments/lib/deployment` directory,
you're golden.

Finally, you need to deploy your containers via `bonfire` so that hot-deployment
is enabled.  This is controlled with the
`JAVA_OPTS_APPEND=-Dspring.devtools.restart.enabled=true` and
`QUARKUS_LAUNCH_DEVMODE=true` parameter settings.  Specifying these over and
over in `bonfire` for each component we have is tedious, so I recommend editing
your `~/.config/bonfire/config.yaml` file and adding the pertinent parameter for
each component.

**Edit your `~/.config/bonfire/config.yaml`** and add
`JAVA_OPTS_APPEND=-Dspring.devtools.restart.enabled=true` as a parameter under
each Spring component, currently `swatch-tally`,
`swatch-producer-red-hat-marketplace`, `swatch-subscription-sync`,
`swatch-system-conduit`, and `swatch-api`.  Then add
`QUARKUS_LAUNCH_DEVMODE=true` to the parameter list for each Quarkus project.

### Usage
Use `deploy` to do a hot-deploy of the Spring code.  (Quarkus support will be in
a later version).

Options:
* `--clean`/`--no-clean`: Whether to run a `gradle clean` as part of the build
  process.  Defaults to `--clean`
* `--project`: The gradle project to build and deploy.
* `--pod-prefix`: The pod prefix to deploy to.  E.g. `swatch-api-service`
* `--container`: The container within the pod to deploy to.  Useful because some
  of our pods use sidecars.  If you don't specify, `swatchdog` will look at all
  the containers in a pod and pick the one that has a `containerPort` named
  `web`.

The `--project` and `--pod-prefix` flags use [`fzf`'s search syntax](https://github.com/junegunn/fzf?tab=readme-ov-file#search-syntax).
I recommend reading the documentation there, but the short summary is

* Prefix your argument with `'` if you want an exact-match, e.g. `--project
  "'root"` to match only the word "root" rather than just any string containing
  the characters "r", "o", "o", and "t".
* Prefix your argument with `^` for a prefix exact-match, e.g. `--pod-prefix
  "^swatch"` to view only the pods that *start* with the string "swatch".

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
