# Getting Started
* `sudo dnf install golang`

* Install `bonfire` following the instructions [here](https://github.com/RedHatInsights/bonfire#installation)

* Configure `bonfire` to use your checkout.  This cat command is just a
  short-cut so the instructions will be succinct.  You should open the file and
  paste in the name and component bits yourself under the `apps:` key.  If you
  paste in the contents, replace `$(pwd)` with the directory where your
  subscription-watch checkout is

  You can override parameters as shown below, or alternatively with the bonfire
  `-p` argument during the deploy step.  The parameters in the example below are
  useful for development environments.

```bash
bonfire config write-default

cat <<BONFIRE >>  ~/.config/bonfire/config.yaml
- name: rhsm #Name of app-sre 'application' folder this component lives in
  components:
    - name: swatch-tally
      host: local
      repo: $(pwd)/swatch-tally
      path: /deploy/clowdapp.yaml
      parameters:
        REPLICAS: 1
        DEV_MODE: "true"
        swatch-tally/IMAGE: quay.io/cloudservices/rhsm-subscriptions
        RHSM_RBAC_USE_STUB: "true"
        
    - name: swatch-producer-red-hat-marketplace
      host: local
      repo: $(pwd)/rhsm-subscriptions/swatch-producer-red-hat-marketplace
      path: /deploy/clowdapp.yaml
      parameters:
        REPLICAS: 1

    - name: swatch-metrics
      host: local
      repo: $(pwd)/swatch-metrics
      path: /deploy/clowdapp.yaml
      parameters:
        DEV_MODE: "true"
        REPLICAS: 1
        swatch-metrics/IMAGE: quay.io/cloudservices/rhsm-subscriptions

    - name: swatch-subscription-sync
      host: local
      repo: $(pwd)/swatch-subscription-sync
      path: /deploy/clowdapp.yaml
      parameters:
        DEV_MODE: "true"
        REPLICAS: 1
        swatch-subscription-sync/IMAGE: quay.io/cloudservices/rhsm-subscriptions

    - name: swatch-system-conduit
      host: local
      repo: $(pwd)/swatch-system-conduit
      path: /deploy/clowdapp.yaml
      parameters:
        REPLICAS: 1
        swatch-system-conduit/IMAGE: quay.io/cloudservices/swatch-system-conduit

    - name: swatch-api
      host: local
      repo: $(pwd)/rhsm-subscriptions/swatch-api
      path: /deploy/clowdapp.yaml
      parameters:
        REPLICAS: 1
        IMAGE: quay.io/cloudservices/rhsm-subscriptions
        RHSM_RBAC_USE_STUB: "true"

    - name: swatch-producer-aws
      host: local
      repo: $(pwd)/rhsm-subscriptions/swatch-producer-aws
      path: /deploy/clowdapp.yaml
      parameters:
        REPLICAS: 1
        swatch-producer-aws/IMAGE: quay.io/cloudservices/swatch-producer-aws
BONFIRE
```

### Ephemeral Development and Deployment
The definitive reference is going to be the "Onboarding to the Ephemeral
Cluster" page in the Cloud-dot documentation, but here are some essentials:

* Make sure you’re part of the
  [RedHatInsights](https://github.com/RedHatInsights) GitHub org and a member of
  the `ephemeral-users` role in your file under the `users` directory in
  app-interface.

* Install `oc` from the `CLI Tools Download Page` on the cluster.

* Activate your virtualenv for Bonfire
  * `source $ENV_LOCATION/bin/activate`
  
* Namespaces can be reserved with `bonfire`.  E.g. `bonfire namespace reserve
  --duration HOURS` will reserve a random available namespace for the number of
  hours you specify.  You can always increase a reservation by reserving the
  namespace again: `bonfire namespace reserve NAMESPACE`.

* Create an account on `quay.io` and create an image repository for each
  component (Currently, one for rhsm-subscriptions and one for
  swatch-system-conduit).  Use `podman login` or `docker login` so that you
  can build and push your test images there.

* You can do the builds with the script in `bin/build-images.sh`.

  By default, bonfire/clowder use the first 7 characters of the git hash as the
  image tag.  Note that currently Clowder has an enforced image pull policy of
  "IfNotPresent" so using a static tag (even "latest") is not a workable option.

* When you deploy with bonfire during development, you'll want to specify the
  image and image tag you want to use like so:

  ```
  bonfire deploy rhsm-subscriptions -n NAMESPACE --no-remove-resources=rhsm-subscriptions
  -i quay.io/my-repo/my-image=my-tag -p rhsm-subscriptions/IMAGE=quay.io/my-repo/my-image
  -i quay.io/my-repo/my-conduit-image=my-tag -p rhsm-subscriptions/CONDUIT_IMAGE=quay.
  io/my-repo/my-conduit-image
  ```

  The `-i` argument overrides the image tag that you're using.  The `-p`
  overrides parameters in specific ClowdApp components (defined in
  `~/.config/bonfire/config.yaml`).  In this case, we override the `IMAGE`
  and `CONDUIT_IMAGE` parameters in our template with the image to use.

  Note that you can also locally change the images used without the
  parameters - simply add `IMAGE` and `CONDUIT_IMAGE` to `parameters` in
  `~/.config/bonfire/config.yaml`. (If you do this, the `-p` arguments to
  `bonfire` are redundant)

  If you don't specify the tag to use with `-i` bonfire is going to use the
  first 7 characters of the git hash for HEAD.  If you don't specify the repo
  with the `-p` argument, `bonfire` is going to use what's defined in the
  ClowdApp which is going to be the production image that's been pushed to the
  official repo.

  The `--no-remove-resources=all` argument is extremely important. Without it,
  bonfire will process the template and will **not** include our resource
  requests. This "feature" is to prevent apps from requesting too much but the
  default resources given are vastly insufficient for our purposes.

* If you want to reset your ephemeral environment from the RHSM stuff entirely,
  you can delete the special "app" resource that Clowder creates.  So `oc delete
  app rhsm` will essentially delete all the pods, deployments, etc. associate
  with RHSM while leaving other apps (like RBAC) in place.

* Expose your pods using `oc port-forward`

* Here's a one-liner to see who has what ephemeral environment reserved

  ```shell
  oc get project -l ephemeral-ns-reserved -L ephemeral-ns-requester-name,ephemeral-ns-reserved
  ```

* Here's a way to monitor events (useful for tracking down deployment issues)

  ```shell
  oc get events --sort-by=.metadata.creationTimestamp
  ```

# Special Notes
## bonfire "deploy" command and namespace reservation
If you use `bonfire deploy` without already having a namespace reserved, it will
reserve the namespace for you **BUT** if the app doesn't start up in the default
amount of time, bonfire will take down/give up the namespace it reserved to
begin with.  To get around this, you can manually reserve the namespace, then
pass `-n <NAMESPACE>` as an argument when running `bonfire deploy`.

# TL;DR Quickstart Steps
1. Start bonfire virtual environment
2. Reserve a namespace
4. Deploy rhsm with `bonfire deploy -n NAMESPACE`
