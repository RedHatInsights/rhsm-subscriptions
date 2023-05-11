# swatch-subscription-sync

## Highlights

* openshift service formally known as "capacity-ingress"
* runs the "capacity-ingress" spring profile
* connects to rhsm & hbi databases
* uses UMB
* logs using splunk HttpEventCollector
* makes http requests to IT product & subscription rest endpoints
* uses quay.io/cloudservices/rhsm-subscriptions image

CronJobs
* subscription-sync
* offering-sync

Kafka Topics
* platform.rhsm-subscriptions.subscription-sync
* platform.rhsm-subscriptions.capacity-reconcile
* platform.rhsm-subscriptions.offering-sync
* platform.rhsm-subscriptions.subscription-prune

## Caching

The product-denylist is cached and the component may need to be rebooted in order to pickup new changes. To reboot you can use hawtio at `{HAWTIO_URL}/app/rhsm-capacity-ingress/hawtio/jmx/operations?nid=root-org.springframework.boot-Endpoint-Shutdown`



## deploying to ephemeral environment

You can use the `-C` option with a bonfire command to define which components of a bonfire `app` definition to deploy.  By default, all components under a bonfire `app` will be deployed.

In order to support this, a couple of placeholders and env vars are defined in the swatch-subscription-sync/deploy/clowdapp.yaml file. The most interesting is the changes needed to support the databases in EE.  Eventually we'll want to define all of our clowdapps as sharing the rhsm database.  Since we don't deploy an rhsm app in stage/prod right now, we can't use that syntax.

### independently from rhsm

```bash
bonfire deploy \
  -C swatch-subscription-sync \
  -i quay.io/cloudservices/rhsm-subscriptions=latest \
  -p swatch-subscription-sync/DB_POD=swatch-subscription-sync \
  --no-remove-resources=swatch-subscription-sync \
rhsm
```

