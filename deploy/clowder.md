* [Install Minikube](https://minikube.sigs.k8s.io/docs/start/)

* ```
  minikube config set cpus 4 &&
  minikube config set memory 16000 &&
  minikube config set disk-size 36GB &&
  minikube config set vm-driver kvm2
  ```

* `minikube start`

* Symlink kubectl from minikube. You may need to clear your shell's cache of path lookups (e.g.
  `hash -d kubectl`.  See [here](https://minikube.sigs.k8s.io/docs/handbook/kubectl/) for 
* information about how `minikube` and `kubectl` work together.

  ```
  sudo ln -s $(which minikube) /usr/local/bin/kubectl
  ```

* Make sure you're using the minikube context: `kubectl config use-context minikube`
  (I believe this context is created for you automatically when you bring the minikube VM up.)

* Create an RHSM namespace and set it to your default in the minikube context

  ```
  kubectl create namespace rhsm &&
  kubectl config set-context minikube --namespace=rhsm
  ```

* `sudo dnf install golang`

* Clone the clowder [git repo](git://github.com/RedHatInsights/clowder), checkout the latest 
* release tag, and run
  `build/kube_setup.sh` to install some prerequisite operators.

* Install the clowder operator. Make sure to check the releases page so that you're using the
  latest version (0.20.0 as of this writing).

  ```
  minikube kubectl -- apply -f
  https://github.com/RedHatInsights/clowder/releases/download/v0.20.0/clowder-manifest-v0.20.0.yaml
  --validate=false
  ```

* Install `bonfire` following the instructions [here](https://github.com/RedHatInsights/bonfire#installation)

* Import the ClowdEnv using `bonfire`.

  ```
  bonfire deploy-env --namespace rhsm -f deploy/dev-clowdenv.yaml
  ```

* Configure `bonfire` to use your checkout.  This cat command is just a short-cut so the
  instructions will be succinct.  You should open the file and paste in the name and component bits
  yourself under the `apps:` key.  If you paste in the contents, replace `$(pwd)` with the
  directory where your subscription-watch checkout is

  ```
  bonfire config write-default
  cat <<BONFIRE >>  ~/.config/bonfire/config.yaml
  - name: rhsm-subscriptions
    components:
      - name: rhsm-subscriptions
        host: local
        repo: $(pwd)
        path: deploy/rhsm-clowdapp.yaml
  BONFIRE
  ```

* The image to use should be publicly available and get pulled automatically, but to import an
  image yourself:
  ```
  minikube ssh
  docker pull quay.io/cloudservices/rhsm-subscriptions:latest
  ```

* Manually create the host-inventory-db-readonly secret.  You can pull values out of 
  vault.devshift.net

---
# Development and Deployment
## Prepare your shell
* Set up shell completion if you like
  * `source <(kubectl completion zsh)` or `bash` if you use bash
  * `source <(minikube completion zsh)` or `bash` if you use bash
* Activate your virtualenv for Bonfire
  * `source $ENV_LOCATION/bin/activate`
* See below for discussion, but if using Docker, point your registry to Minikube
  * `eval $(minikube -p minikube docker-env)`

Make your life easier and install the [Kubernetes Lens IDE](https://k8slens.dev/).  It's much
easier to use than the browser console.

It is easiest if you configure your container management program to work directly with the
minikube container registry.  Theoretically, this is possible with `podman` via the `podman
--remote` option or special-built `podman-remote` command (I am unsure what the actual runtime
difference is between these two); however, `minikube` is build with an older version of `podman`
and the API is incompatible with the newer version of `podman` in Fedora.

The alternative is to use `docker`.  To install `docker` on more recent Fedora versions (F33 is
what I am using), run `sudo dnf install moby-engine`.  Make sure you do not have the
`podman-docker` package installed because it will conflict over `/usr/bin/docker`
(`podman-docker` is basically just a shell script that delegates all `docker` invocations to
`podman`).

Once Docker is installed, run `eval $(minikube -p minikube docker-env)` to source minikube
provided values for `DOCKER_HOST` and other environment variables.  You can now run Docker
builds and the results will appear directly in the minikube Docker registry without having to
push them: a nice little trick to save some labor.

Make your code changes and when you're done run `docker build -t localhost/swatch:scratch .`.  I
use the `scratch` tag based on a warning in an older version of the
[minikube documentation](https://v1-18.docs.kubernetes.io/docs/setup/learning-environment/minikube/#use-local-images-by-re-using-the-docker-daemon)
that reads "Note: Be sure to tag your Docker image with something other than latest and use that tag
to pull the image. Because :latest is the default value, with a corresponding default image pull
policy of Always, an image pull error (ErrImagePull) eventually results if you do not have the
Docker image in the default Docker registry (usually DockerHub)."

Set the `IMAGE` and `IMAGE_TAG` values in the `ClowdApp` template and then run
`bonfire deploy rhsm-subscriptions -n rhsm -i localhost/swatch=scratch`  to deploy.

I have specifically defined the image tag here because otherwise, `bonfire` will actually run a
`git rev-parse HEAD` and attempt to use that hash as an image tag.  Figuring out how that's meant
to work is on the TODO list.

Once your pods are up and running, you can expose them directly or the associated
service using `port-forward`.  For example: `kubectl port-forward svc/rhsm-clowdapp-api 8000`
will forward your localhost's port 8000 to the service (and underlying pod).
