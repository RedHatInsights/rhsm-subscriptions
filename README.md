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
