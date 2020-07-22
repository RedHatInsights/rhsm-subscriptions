# Local Deployment

## Deploy insights-inventory

rhsm-subscriptions requires a connection to insights-inventory. First set up a
postgres user and database.

```
su - postgres
createuser --pwprompt -d insights
```

Run the `bin/deploy-insights` script to install the insights-inventory
project and begin running it on port 8080 by default. Check the `--help`
to see all the available options. This script will init the
git-submodule if it hasn't been already, run the database migration from
`manage.py` and then start the Flask application with the appropriate
environment variables.

Once the app has started, check to make sure you can access the API
(keep in mind that you may need to adjust the port number in the curl
command if you used a different port for deployment).

```
curl http://localhost:8080/metrics
```

## Build and Run rhsm-subscriptions

In order to build rhsm-subscriptions, make sure you have Java SDK 8 installed
(Java 1.8.x).

Create a PostgreSQL role for the application:

```
su - postgres
createuser --pwprompt -d rhsm-subscriptions
```

Run the `bin/init-application` script (with appropriate arguments if the
defaults don't suit you) to make sure the database is created and other
initialization tasks are handled.

Build and run using the following line (you can leave off the `--args` if you
are happy to run the application off port 8080)

```
./gradlew bootRun --args="--server.port=9166"
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
