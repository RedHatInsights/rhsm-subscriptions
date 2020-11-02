# Local Deployment

## Prerequisites

First, ensure you have podman-compose, podman and java 11 installed:

```
sudo dnf install -y podman-compose podman java-11-openjdk-devel
```

Ensure the checkout has the HBI submodule initialized:

```
git submodule update --init --recursive
```

## Dependent services

Start via:

```
podman-compose up -d
```

*NOTE*: if the DB hasn't finished starting up (likely), HBI will fail to
start, to remedy: `podman start rhsm-subscriptions_inventory_1`.

Stop via (backup data first if desired):
```
podman-compose down
```

For more details about what services are defined, see `container-compose.yml`

Note that the compose assumes that none of the services are already running
locally (hint: might need to `sudo systemctl stop postgresql`). If you want to
use only some of the services via podman-compose, then `podman-compose up
--no-start` can be used to define the services (you can then subsequently
manually start containers for the services you wish to deploy locally.

If you prefer to use local postgresql service, you can use `init_dbs.sh`.

## Build and Run rhsm-subscriptions

```
./gradlew bootRun
```

Spring Boot [defines many properties](https://docs.spring.io/spring-boot/docs/2.3.4.RELEASE/reference/htmlsingle/#common-application-properties)
that can be overridden via args or environment variables. (We prefer
environment variables). To determine the environment variable name,
uppercase, remove dashes and replace `.` with `_` (per
[Spring docs](https://docs.spring.io/spring-boot/docs/2.3.4.RELEASE/reference/htmlsingle/#boot-features-external-config-relaxed-binding-from-environment-variables))
We also define a number of service-specific properties (see [Environment Variables](#environment-variables))

For example, the `server.port` (or `SERVER_PORT` env var) property changes the listening port:

```
SERVER_PORT=9090 ./gradlew bootRun
```

## Deployment Notes

RHSM Subscriptions is meant to be deployed under the context path "/". The
location of app specific resources are then controlled by the
`rhsm-subscriptions.package_uri_mappings.org.candlepin.insights` property.
This unusual configuration is due to external requirements that our
application base its context path on the value of an environment
variable. Using "/" as the context path means that we can have certain
resources (such as health checks) with a known, static name while others
can vary based on an environment variable given to the pod.

### Static Endpoints

* /actuator/health - A Spring Actuator that we use as OKD
  liveness/readiness probe.
* /actuator/info - An actuator that reads the information from
  `META-INF/build-info.properties` and reports it. The response includes
  things like the version number.

Both the health actuator and info actuator can be modified, expanded, or
extended. Please see the
[documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html)
for a discussion of extension points.

### RBAC

rhsm-subscriptions uses an RBAC service to determine application authorization. The
RBAC service can via configured by environment variables (see below).

For development purposes, the RBAC service can be stubbed out so that the connection
to the RBAC service is bypassed and all users recieve the 'subscriptions:*:*' role. This
can be enabled by setting `RBAC_USE_STUB=true`

```sh
RBAC_USE_STUB=true ./gradlew bootRun
```

### Environment Variables

* `DEV_MODE`: disable anti-CSRF, account filtering, and RBAC role check
* `PRETTY_PRINT_JSON`: configure Jackson to indent outputted JSON
* `APP_NAME`: application name for URLs (default: rhsm-subscriptions)
* `PATH_PREFIX`: path prefix in the URLs (default: api)
* `INVENTORY_DATABASE_HOST`: inventory DB host
* `INVENTORY_DATABASE_DATABASE`: inventory DB database
* `INVENTORY_DATABASE_USERNAME`: inventory DB user
* `INVENTORY_DATABASE_PASSWORD`: inventory DB password
* `PRODUCT_WHITELIST_RESOURCE_LOCATION`: location of the product whitelist
* `ACCOUNT_LIST_RESOURCE_LOCATION`: location of the account list (opt-in used otherwise)
* `DATABASE_HOST`: DB host
* `DATABASE_PORT`: DB port
* `DATABASE_DATABASE`: DB database
* `DATABASE_USERNAME`: DB username
* `DATABASE_PASSWORD`: DB password
* `CAPTURE_SNAPSHOT_SCHEDULE`: cron schedule for capturing tally snapshots
* `ACCOUNT_BATCH_SIZE`: number of accounts to tally at once
* `TALLY_RETENTION_DAILY`: number of daily tallies to keep
* `TALLY_RETENTION_WEEKLY`: number of weekly tallies to keep
* `TALLY_RETENTION_MONTHLY`: number of monthly tallies to keep
* `TALLY_RETENTION_QUARTERLY`: number of quarterly tallies to keep
* `TALLY_RETENTION_YEARLY`: number of yearly tallies to keep
* `KAFKA_TASK_GROUP`: kafka task group
* `KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS`: kafka max poll interval in milliseconds
* `KAFKA_MESSAGE_THREADS`: number of consumer threads
* `KAFKA_BOOTSTRAP_HOST`: kafka bootstrap host
* `KAFKA_BOOTSTRAP_PORT`: kafka boostrap port
* `KAFKA_CONSUMER_RECONNECT_BACKOFF_MS`: kafka consumer reconnect backoff in milliseconds
* `KAFKA_CONSUMER_RECONNECT_BACKOFF_MAX_MS`: kafka consumer reconnect max backoff in milliseconds
* `KAFKA_API_RECONNECT_TIMEOUT_MS`: kafka connection timeout in milliseconds
* `KAFKA_SCHEMA_REGISTRY_SCHEME`: avro schema server scheme (http or https)
* `KAFKA_SCHEMA_REGISTRY_HOST`: kafka schema server host
* `KAFKA_SCHEMA_REGISTRY_PORT`: kafka schema server port
* `KAFKA_AUTO_REGISTER_SCHEMAS`: enable auto registration of schemas
* `RBAC_USE_STUB`: stub out the rbac service
* `RBAC_APPLICATION_NAME`: name of the RBAC permission application name (`<APP_NAME>:*:*`), by default this property is set to 'subscriptions'.
* `RBAC_HOST`: RBAC service hostname
* `RBAC_PORT`: RBAC service port
* `RBAC_MAX_CONNECTIONS`: max concurrent connections to RBAC service
* `CLOUDIGRADE_ENABLED`: set to `true` to query cloudigrade for RHEL usage
* `CLOUDIGRADE_MAX_ATTEMPTS`: maximum number of attempts to query cloudigrade
* `CLOUDIGRADE_HOST`: cloudigrade service host
* `CLOUDIGRADE_PORT`: cloudigrade service port
* `CLOUDIGRADE_MAX_CONNECTIONS`: max concurrent connections to cloudigrade service

## Deploy to Openshift

Prerequisite secrets:

- `rhsm-db`: DB connection info, having `db.host`, `db.port`, `db.user`, `db.password`, and `db.name` properties.
- `host-inventory-db-readonly`: inventory read-only clone DB connection info, having `db.host`, `db.port`, `db.user`, `db.password`, and `db.name` properties.
- `ingress`: secret with `keystore.jks` and `truststore.jks` - keystores for mTLS communication with subscription-conduit.
- `tls`: having `keystore.password`, the password used for capacity ingress.

Prequisite configmaps:
- `capacity-allowlist` having `product-allowlist.txt` which is a newline-separated list of which SKUs have been approved for capacity ingress.

Adjust as desired:

```
oc process -f templates/rhsm-subscriptions-api.yml | oc create -f -
oc process -f templates/rhsm-subscriptions-capacity-ingress.yml | oc create -f -
oc process -f templates/rhsm-subscriptions-scheduler.yml | oc create -f -
oc process -f templates/rhsm-subscriptions-worker.yml | oc create -f -
```

## Release Notes

You can perform a release using `./gradlew release` **on the master
branch**. This command will invoke a
[plugin](https://github.com/researchgate/gradle-release) that will bump
the version numbers and create a release tag. When you run the command,
it will ask for input. You can specify all the arguments on the CLI if
necessary (e.g. doing a release via a CI environment). When asked for
"This release version" specify the current version **without** the
"-SNAPSHOT". When asked for the next version number enter the
appropriate value prefixed **with** "-SNAPSHOT". The plugin should offer
sane defaults that you can use.

For example. If we are working on `1.0.0-SNAPSHOT`, you would answer the
first question with `1.0.0` and the second question with
`1.0.1-SNAPSHOT`.

The plugin will create the tag and bump the version. You just need to
push with `git push --follow-tags origin master`.
