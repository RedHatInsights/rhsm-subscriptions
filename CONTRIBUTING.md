Preferred Package Structure
---------------------------

* config 
* resource
  * http "entrypoints", extend resteasy Resource interfaces that are generated with openapi generator 
  * resource should have an injected service and not inject a repository class directly
* model
  * Dtos and other pojos 
* MapStruct mappers 
* exception 
  * extending Exception
* service 
  * Business logic, orchestration, things that aren't appropriate in the other packages 
  * entities should be converted to DTO in this layer prior to being returned to resource classes 
  * kafka producer and consumers live here 
  * umb communication lives here 
  * interact with the database by having a service class inject a repository class 
* repository 
  * PanacheRepository and JpaRepository 
  * JPA entities 
    * Class names should have "Entity" suffix to differentiate them from their DTO counterparts
* security
  * authentication, authorization, RBAC, etc.
-------------
# Build

Requirements to build the repository are:
- Gradle 7.4+
- Java 17
- Podman with socket listening (instructions [here](#podman-installation-instructions)) / or Docker installed

Once we have all the requirements properly installed and configured, we can build the repository by doing:

```shell
./gradle clean build
```

-------------
# Code Style

- Disable wildcard imports

To disable it in the IntelliJ IDEA editor, navigate to _Editor_ -> _Code Style_ -> _Java_ -> _Imports_
and set _Class count to use import with '\*'_ to `999`. Do the same with _Names count to use static import with '\*'_.

-------------
# Formatting

Before submit pull requests, remember to format your changes using:

```shell
./gradlew spotlessApply
```

Otherwise, the CI will detect your changes are not formatted and reject your pull request.

-------------
# DB migrations

We use [liquibase](https://docs.liquibase.com/) to manage our DB schema.

* `./gradlew :liquibaseUpdate` runs DB migrations
* `./gradlew :liquibaseRollbackCount -PliquibaseCommandValue=1` rolls back a single DB changeset

You can use a prefix to target different subprojects when they have liquibase enabled:

```shell
./gradlew swatch-contracts:liquibaseUpdate
```

There are various other tasks, see `./gradlew help tasks | grep liquibase`. Note that we don't use
Liquibase Hub or Liquibase Pro.

DB migrations should be written to roll back cleanly (exceptions should be discussed with the team).

-------------
# swatch-product-configuration

Directory structure: `$Platform/$Subscription_Name.yaml` (e.g. RHEL/RHEL_for_x86.yaml).

Use camel case for yaml properties that need to be serialized into the `com.redhat.swatch.configuration.registry` object definitions.

The `includedSubscriptions` property only applies to metrics that aren't sourced from prometheus.  It's used to prevent double counting capacity.

-------------
# Podman installation instructions

Since we're using Testcontainers to set up services during test execution, besides installing podman, you also need to enable podman socket with 
Docker REST API.

First, you need to install podman. For Linux systems, you need to do:

```shell
sudo dnf install podman podman-docker
```

In other systems, follow [the official site guide](https://podman.io/docs/installation) to install podman.

Once installed, you need to enable the podman socket with Docker REST API with systemd (only needs to be done once):

```shell
systemctl --user enable podman.socket --now
```

In systems when systemd is not running (WSL etc), instead of the above command, you need to do:

```shell
podman system service --time=0
```

Then, you can obtain the path of the socket with the following command:

```shell
$ podman info | grep -A2 'remoteSocket'

remoteSocket:
  path: /path/to/podman.sock
```
