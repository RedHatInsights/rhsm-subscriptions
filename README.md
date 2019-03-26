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
export INVENTORY_DB_NAME=inventory
pipenv install --dev
pipenv shell
python manage.py db upgrade
LISTEN_PORT=8080 INVENTORY_LOGGING_CONFIG_FILE=logconfig.ini prometheus_multiproc_dir=./prometheumultiprocess/ FLASK_DEBUG=1 INVENTORY_DB_NAME="inventory" INVENTORY_SHARED_SECRET=mysecret python run.py
```

Once the app has started, check to make sure you can access the API
```
curl http://localhost:8080/metrics
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
oc new-app --template=rhsm-insights-inventory -p INVENTORY_DB_HOSTNAME=<YOUR_DATABASE_HOSTNAME> -p INVENTORY_SHARED_SECRET=<YOUR_SHARED_SECRET>
```

Test the installation:
```
curl http://<your_route_address>/metrics
```

## Deploying Kafka

### Deploying via Docker

A local Kafka cluster can be deployed very quickly via docker. This deployment includes
a web interface that allows you to monitor messages and kafka statistics. This is useful
for development.

See: https://github.com/Landoop/fast-data-dev

```
docker run --rm --net host landoop/fast-data-dev
```

### Deploying to Minishift

A project named [Strimzi](https://strimzi.io) has already done most of
the heavy lifting around getting Kafka running in Openshift. As of this
writing, the latest release is 0.11, but I was unable to get it to work.
Instead I used 0.10 which you can download
[here](https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.10.0/strimzi-0.10.0.tar.gz)
or browse to from the
[releases page](https://github.com/strimzi/strimzi-kafka-operator/releases).

Once you download the release, extract the tarball and `cd` into the
directory. Make sure minishift is up and running.

* Log in to minishift as the administrator:

  ```
  % oc login -u system:admin
  ```
* Configure the cluster operator YAML files to your namespace. In this
  example, I am using the namespace `conduit`:

  ```
  % sed -i 's/namespace: .*/namespace: conduit/' install/cluster-operator/*RoleBinding*.yaml
  ```
* Install the cluster operator into your namespace. In this example, the
  namespace is `conduit`:

  ```
  % oc apply -f install/cluster-operator -n conduit
  % oc apply -f examples/templates/cluster-operator/ -n conduit
  ```
* Provision the cluster. You have two options here: ephemeral or
  persistent storage. The YAML for both is under the `examples/kafka`
  directory. By default the cluster will be named `my-cluster` but you
  can rename it by editing the YAML. For the ephemeral storage into the
  `conduit` namespace:

  ```
  % oc apply -f examples/kafka/kafka-ephemeral.yaml -n conduit
  ```

  or for persistent storage. I have personally not had success with the
  persistent storage profile.

  ```
  % oc apply -f examples/kafka/kafka-persistent-single.yaml -n conduit
  ```

* Keep in mind that you can back out any of these changes by using `oc
  delete -f <YAML file> -n <namespace>`

* Once you provision the cluster, give it a little while for all the
  pods to come up (you can watch with `oc get pods -n conduit -w`). I
  have seen issues with pods not coming up. I was able to solve the
  problem by restarting minishift.
