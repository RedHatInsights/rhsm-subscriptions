Run using

```
./gradlew build && java -jar build/libs/rhsm-conduit-1.0.0.jar
```

## OpenShift Project Set Up

Choose a project to deploy to, and set up a `rhsm-conduit-config` ConfigMap for that project.
There is an example config files in `openshift/example_config/rhsm-conduit.conf`, that can be applied via:

```
oc create configmap rhsm-conduit-config --from-file openshift/example_config
```

Also, set up rhsm-conduit secrets:

```
oc create -f openshift/secret-rhsm-conduit_dummy.yaml
```

The secrets are used for client certificates, so having an empty secret is acceptable if client certificates are not used.

## Deploying to Openshift

First, log in to an openshift instance. Make sure the project has been set up (see previous section).

```
oc create -f openshift/template_rhsm-conduit.yaml  # add a template for deploying rhsm-conduit
oc new-app --template=rhsm-conduit  # deploy an instance of rhsm-conduit using the template
```

By default, the template deploys the master branch of rhsm-conduit. If it's more appropriate to deploy a different branch (e.g. production), then use:

```
oc new-app --template=rhsm-conduit -p SOURCE_REPOSITORY_REF=production
```

If, for debugging on a local machine, for convenience, you need a route to test rhsm-conduit,

```
oc create -f openshift/template_rhsm-conduit-route.yaml
oc new-app --template=rhsm-conduit-route
```

## Deploying insights-inventory

rhsm-conduit requires a connection to insights-inventory.

### Local Deployment

First set up a postgres user and database.
```
su - postgres
createuser --pwprompt -d insights
createdb -U insights inventory
```

Next install insights-inventory. Requires pipenv to be installed.

```
git clone https://github.com/RedHatInsights/insights-host-inventory.git
cd insights-host-inventory
mkdir ./prometheumultiprocess
pipenv install --dev
pipenv shell
python manage.py db upgrade
LISTEN_PORT=8080 INVENTORY_LOGGING_CONFIG_FILE=logconfig.ini prometheus_multiproc_dir=./prometheumultiprocess/ FLASK_DEBUG=1 NOAUTH=1 INVENTORY_DB_NAME="inventory" python run.py
```

Once the app has started, check to make sure you can access the API
```
curl http://localhost:8080/metrics
curl http://localhost:8080/r/insights/platform/inventory/api/v1/hosts
```


### Deploying to minishift/openshift

First you'll need to deploy an instance of postgres.
```
oc new-app --template=postgresql-persistent -p POSTGRESQL_USER=insights -p POSTGRESQL_PASSWORD=insights -p POSTGRESQL_DATABASE=inventory
```

NOTE: The hostname of the database service will be: postgres.<project_namespace>.svc
      You can use the console to find the hostname via: Applications ->  Services -> postgresql

Create a new insights-inventory template. Take note of the route for the new app.

```
oc create -f openshift/template_insights-inventory.yaml
oc new-app --template=rhsm-insights-inventory -p INVENTORY_DB_HOSTNAME=<YOUR_DATABASE_HOSTNAME>
```

Test the installation:
```
curl http://<your_route_address>/metrics
curl http://<your_route_address>/r/insights/platform/inventory/api/v1/hosts
```
