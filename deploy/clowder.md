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

  ```
  bonfire config write-default

  cat <<BONFIRE >>  ~/.config/bonfire/config.yaml
  - name: rhsm
    components:
      - name: capacity-allowlist
        host: gitlab
        repo: rhsm/swatch-product-allowlist
        path: templates/capacity-allowlist.yml
      - name: rhsm-subscriptions
        host: local
        repo: $(pwd)
        path: deploy/rhsm-clowdapp.yaml
        parameters:
            REPLICAS: 1
            RHSM_RBAC_USE_STUB: 'true'
            RH_MARKETPLACE_MANUAL_SUBMISSION_ENABLED: 'true'
            DEV_MODE: 'true'
            ENABLE_ACCOUNT_RESET: 'true'
      - name: swatch-producer-aws
        host: local
        repo: $(pwd)
        path: swatch-producer-aws/deploy/clowdapp.yaml
        parameters:
          AWS_MANUAL_SUBMISSION_ENABLED: 'true'

  BONFIRE
  ```
* Make your life easier and install the [Kubernetes Lens
  IDE](https://k8slens.dev/).  It's much easier to use than the browser console.

## Local Development
You can do development locally using Minikube, but be aware that the resource
requirements are going to be **steep**.

* [Install Minikube](https://minikube.sigs.k8s.io/docs/start/)

* ```
  minikube config set cpus 4 &&
  minikube config set memory 16000 &&
  minikube config set disk-size 36GB &&
  minikube config set vm-driver kvm2
  ```

* `minikube start`

* Symlink kubectl from minikube. You may need to clear your shell's cache of
  path lookups (e.g.  `hash -d kubectl`.  See
  [here](https://minikube.sigs.k8s.io/docs/handbook/kubectl/) for information
  about how `minikube` and `kubectl` work together.

  ```
  sudo ln -s $(which minikube) /usr/local/bin/kubectl
  ```

* Make sure you're using the minikube context: `kubectl config use-context
  minikube` (I believe this context is created for you automatically when you
  bring the minikube VM up.)

* Create an RHSM namespace and set it to your default in the minikube context

  ```
  kubectl create namespace rhsm &&
  kubectl config set-context minikube --namespace=rhsm
  ```

* Clone the clowder [git repo](git://github.com/RedHatInsights/clowder),
  checkout the latest release tag, and run `build/kube_setup.sh` to install some
  prerequisite operators.

* Install the clowder operator. Make sure to check the releases page so that
  you're using the latest version (0.20.0 as of this writing).

  ```
  minikube kubectl -- apply -f
  https://github.com/RedHatInsights/clowder/releases/download/v0.20.0/clowder-manifest-v0.20.0.yaml
  --validate=false
  ```

* Import the ClowdEnv using `bonfire`.

  ```
  bonfire deploy-env --namespace rhsm -f deploy/dev-clowdenv.yaml
  ```

* The image to use should be publicly available and get pulled automatically,
  but to import an image yourself:
  ```
  minikube ssh
  docker pull quay.io/cloudservices/rhsm-subscriptions:latest
  ```

* Manually create the host-inventory-db-readonly secret.  You can pull values
  out of vault.devshift.net

### Development and Deployment
#### Prepare your shell
* Set up shell completion if you like
  * `source <(kubectl completion zsh)` or `bash` if you use bash
  * `source <(minikube completion zsh)` or `bash` if you use bash
* Activate your virtualenv for Bonfire
  * `source $ENV_LOCATION/bin/activate`
* See below for discussion, but if using Docker, point your registry to Minikube
  * `eval $(minikube -p minikube docker-env)`

If you're using minikube, it is easiest if you configure your container
management program to work directly with the minikube container registry.
Theoretically, this is possible with `podman` via the `podman --remote` option
or special-built `podman-remote` command (I am unsure what the actual runtime
difference is between these two); however, `minikube` is build with an older
version of `podman` and the API is incompatible with the newer version of
`podman` in Fedora.

The alternative is to use `docker`.  To install `docker` on more recent Fedora
versions (F33 is what I am using), run `sudo dnf install moby-engine`.  Make
sure you do not have the `podman-docker` package installed because it will
conflict over `/usr/bin/docker` (`podman-docker` is basically just a shell
script that delegates all `docker` invocations to `podman`).

Once Docker is installed, run `eval $(minikube -p minikube docker-env)` to
source minikube provided values for `DOCKER_HOST` and other environment
variables.  You can now run Docker builds and the results will appear directly
in the minikube Docker registry without having to push them: a nice little trick
to save some labor.

Make your code changes and when you're done run `docker build -t
localhost/swatch:scratch .`.  I use the `scratch` tag based on a warning in an
older version of the [minikube
documentation](https://v1-18.docs.kubernetes.io/docs/setup/learning-environment/minikube/#use-local-images-by-re-using-the-docker-daemon)
that reads "Note: Be sure to tag your Docker image with something other than
latest and use that tag to pull the image. Because :latest is the default value,
with a corresponding default image pull policy of Always, an image pull error
(ErrImagePull) eventually results if you do not have the Docker image in the
default Docker registry (usually DockerHub)."

Run `bonfire deploy rhsm-subscriptions -n rhsm -i localhost/swatch=scratch`  to
deploy.

I have specifically defined the image tag here because otherwise, `bonfire` will
actually run a `git rev-parse HEAD` and attempt to use that hash as an image
tag.

Once your pods are up and running, you can expose them directly or the associated
service using `port-forward`.  For example: `kubectl port-forward svc/rhsm-api 8000`
will forward your localhost's port 8000 to the service (and underlying pod).

## Ephemeral Development
This approach is the recommended one.  The definitive reference is going to be
the "Onboarding to the Ephemeral Cluster" page in the Clouddout documentation,
but here are some essentials:

* Make sure you’re part of the
  [RedHatInsights](https://github.com/RedHatInsights) GitHub org and a member of
  the `ephemeral-users` role in your file under the `users` directory in
  app-interface.

* Install `oc` from the `CLI Tools Download Page` on the cluster.

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

* In order to turn on sidecar support in an ephemeral environment:
  `bonfire deploy-env -f deploy/rhsm-eph-clowdenv.yaml -n NAMESPACE`

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

  ```
  oc get project -l ephemeral-ns-reserved -L ephemeral-ns-requester-name,ephemeral-ns-reserved
  ```

# Special Notes
## capacity-allowlist ConfigMap
The capacity-ingress pod relies on a ConfigMap, called `capacity-allowlist`,
found in GitLab.  The most straightforward way to get this config map is to
manually add it via command line.  Make sure you're in the appropriate namespace
using the `oc project` command, and then you can deploy the ConfigMap to that
namespace using the following command:

```
curl https://gitlab.cee.redhat.com/rhsm/swatch-product-allowlist/-/raw/main/templates/capacity-allowlist.yml | oc process -f - | oc apply -f -
```
## bonfire "deploy" command and namespace reservation
If you use `bonfire deploy` without already having a namespace reserved, it will
reserve the namespace for you **BUT** if the app doesn't start up in the default
amount of time, bonfire will take down/give up the namespace it reserved to
begin with.  To get around this, you can manually reserve the namespace, then
pass `-n <NAMESPACE>` as an argument when running `bonfire deploy`.

# TL;DR Quickstart Steps
1. Start bonfire virtual environment
2. Reserve a namespace
3. Create the sidecar enabled ClowdEnv with `bonfire deploy-env -f deploy/rhsm-eph-clowdenv.yaml -n NAMESPACE`
4. Apply the `capacity-allowlist` configmap
5. Deploy rhsm with `bonfire deploy -n NAMESPACE`
