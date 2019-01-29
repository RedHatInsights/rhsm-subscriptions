Run using

```
./gradlew build && java -jar build/libs/rhsm-conduit-1.0.0.jar
```

## Deploying to Openshift

First, log in to an openshift instance.

```
oc create -f openshift/template_rhsm-conduit.yaml  # add a template for deploying rhsm-conduit
oc new-app --template=rhsm-conduit  # deploy an instance of rhsm-conduit using the template
```
