# Local Deployment

## Deploy insights-inventory

### Local Install Via Our Custom Script

NOTE: As of right now, our scripts do not support configuring insights inventory kafka message service.

First set up a postgres user and database.

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

### Install Via Docker

Insights inventory makes it pretty straightforward to install all components via docker.

**NOTE: The supporting code for this is NOT currently checked into the inventory service's master branch. You will
need to check out the ```origin/split_inventory_service``` branch.**


1. Start a kafka and DB instance (unless you reconfigure the scripts you will need to stop your local postgres instance)
    ```
    $ docker-compose -f dev.yml up
    ```

1. Prep the inventory database
```
make upgrade_db
```

1. Add a host entry in ```/etc/hosts``` pointing to the kafka instance that is running on localhost:
    ```
    127.0.0.1      kafka
    ```

1. Run the kafka host message listener service:
    ```
    $ make run_inv_mq_service
    ```

1. Run the inventory web service
    ```
    $ make run_inv_web_service
    ```


## Build and Run rhsm-conduit

In order to build rhsm-conduit, make sure you have Java SDK 8 installed
(Java 1.8.x).

Build and run using the following line:

```
./gradlew assemble && java -jar build/libs/rhsm-conduit-1.0.0.jar
```

Because setting up the Pinhead keystore can be difficult, consider starting with:

```sh
PINHEAD_USE_STUB=true ./gradlew bootRun
```

# Remote Deployment

## OpenShift Project Set Up

Choose a project to deploy to, and set up a `rhsm-conduit-config`
ConfigMap for that project. There is an example config files in
`openshift/example_config/rhsm-conduit.conf`, that can be applied via:

```
oc create configmap rhsm-conduit-config --from-file openshift/example_config
```

Also, set up rhsm-conduit secrets:

```
oc create -f openshift/secret-rhsm-conduit_dummy.yaml
```

The secrets are used for client certificates, so having an empty secret
is acceptable if client certificates are not used.

## Deploy insights-inventory

First you'll need to deploy an instance of postgres.
```
oc new-app --template=postgresql-persistent -p POSTGRESQL_USER=insights -p POSTGRESQL_PASSWORD=insights -p POSTGRESQL_DATABASE=inventory
```

NOTE: The hostname of the database service will be
`postgres.<project_namespace>.svc`. You can use the console to find the
hostname via: Applications -> Services -> postgresql

Create a new insights-inventory template. Take note of the route for the
new app.

```
oc create -f openshift/template_insights-inventory.yaml
oc new-app --template=rhsm-insights-inventory -p INVENTORY_DB_HOSTNAME=<YOUR_DATABASE_HOSTNAME> -p INVENTORY_SHARED_SECRET=<YOUR_SHARED_SECRET>
```

Test the installation:
```
curl http://<your_route_address>/metrics
```

## Deploy to Openshift

First, log in to an openshift instance. Make sure the project has been
set up (see previous section).

```
# add a template for deploying rhsm-conduit
oc create -f openshift/template_rhsm-conduit.yaml
oc new-app --template=rhsm-conduit  # deploy an instance of rhsm-conduit using the template
```

By default, the template deploys the master branch of rhsm-conduit. If
it's more appropriate to deploy a different branch (e.g. production),
then use:

```
oc new-app --template=rhsm-conduit -p SOURCE_REPOSITORY_REF=production
```

If, for debugging on a local machine, for convenience, you need a route
to test rhsm-conduit,

```
oc create -f openshift/template_rhsm-conduit-route.yaml
oc new-app --template=rhsm-conduit-route
```

## Deployment Notes

RHSM Conduit is meant to be deployed under the context path "/". The
location of conduit specific resources are then controlled by the
`rhsm-conduit.package_uri_mappings.org.candlepin.insights` property.
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

### Environment Variables

* `PRETTY_PRINT_JSON`: configure Jackson to indent outputted JSON
* `APP_NAME`: application name for URLs (default: rhsm-conduit)
* `PATH_PREFIX`: path prefix in the URLs (default: api)
* `TASK_QUEUE_TYPE`: `in-memory` or `kafka`
* `ORG_SYNC_SCHEDULE`: cron schedule for syncing hosts
* `ORG_SYNC_STRATEGY`: file-based or DB-based sync strategy
* `ORG_SYNC_RESOURCE_LOCATION`: location of resource with org sync list (if using file-based sync strategy)
* `PINHEAD_USE_STUB`: Use pinhead stub
* `PINHEAD_URL`: Pinhead service URL
* `PINHEAD_KEYSTORE`: path to keystore with client cert
* `PINHEAD_KEYSTORE_PASSWORD`: pinhead client cert keystore password
* `PINHEAD_BATCH_SIZE`: host sync batch size
* `PINHEAD_MAX_CONNECTIONS`: maximum concurrent connections to pinhead
* `INVENTORY_USE_STUB`: Use stubbed inventory REST API
* `INVENTORY_API_KEY`: API key for inventory service
* `INVENTORY_HOST_LAST_SYNC_THRESHOLD`: reject hosts that haven't checked in since this duration (e.g. 24h)
* `INVENTORY_ENABLE_KAFKA`: whether kafka should be used (inventory API otherwise)
* `INVENTORY_HOST_INGRESS_TOPIC`: kafka topic to emit host records
* `INVENTORY_ADD_UUID_HYPHENS`: whether to add missing UUID hyphens to the Insights ID
* `DATABASE_HOST`: DB host
* `DATABASE_PORT`: DB port
* `DATABASE_DATABASE`: DB database
* `DATABASE_USERNAME`: DB username
* `DATABASE_PASSWORD`: DB password
* `KAFKA_TASK_GROUP`: kafka task group
* `KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS`: kafka max poll interval in milliseconds
* `KAFKA_MESSAGE_THREADS`: number of consumer threads
* `KAFKA_BOOTSTRAP_HOST`: kafka bootstrap host
* `KAFKA_BOOTSTRAP_PORT`: kafka boostrap port
* `KAFKA_CONSUMER_RECONNECT_BACKOFF_MS`: kafka consumer reconnect backoff in milliseconds
* `KAFKA_CONSUMER_RECONNECT_BACKOFF_MAX_MS`: kafka consumer reconnect max backoff in milliseconds
* `KAFKA_API_RECONNECT_TIMEOUT_MS`: kafka connection timeout
* `KAFKA_SCHEMA_REGISTRY_SCHEME`: avro schema server scheme (http or https)
* `KAFKA_SCHEMA_REGISTRY_HOST`: kafka schema server host
* `KAFKA_SCHEMA_REGISTRY_PORT`: kafka schema server port
* `KAFKA_AUTO_REGISTER_SCHEMAS`: enable auto registration of schemas
* `DEV_MODE`: turning this on enables developer-centric features.
  * The OrgSyncJob will be run on a cron schedule defined by
    rhsm-conduit.org-sync.schedule

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

## Kafka

See the detailed notes [here](README-kafka.md)
