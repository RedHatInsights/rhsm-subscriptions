* [Install Minikube](https://minikube.sigs.k8s.io/docs/start/)

* ```
  minikube config set cpus 4
  minikube config set memory 16000
  minikube config set disk-size 36GB
  minikube config set vm-driver kvm2
  ```

* `minikube start`

* Symlink kubectl from minikube. You may need to clear your shell's cache of path lookups (e.g.
  `hash -d kubectl`.

  ```
  sudo ln -s $(which minikube) /usr/local/bin/kubectl
  ```

* Make sure you're using the minikube context: `kubectl config use-context minikube`
  (I believe this context is created for you automatically when you bring the minikube VM up.)

* Create an RHSM namespace

  ```
  kubectl create namespace rhsm
  ```

* `sudo dnf install golang`

* Clone the clowder [git repo](git://github.com/RedHatInsights/clowder) and run
  `build/kube_setup.sh` to install some prerequisite operators.

* Install the clowder operator. Make sure to check the releases page so that you're using the 
  latest version (0.14.0 as of this writing).
  
  ```
  minikube kubectl -- apply -f
  https://github.com/RedHatInsights/clowder/releases/download/0.14.0/clowder-manifest-0.14.0.yaml
  --validate=false`
  ```
  
* Install `bonfire` following the instructions at https://github.com/RedHatInsights/bonfire#installation

* Import the ClowdEnv using `bonfire`.

  ```
  bonfire deploy-env -e rhsm -f deploy/dev-clowdenv.yml
  ```

* Configure `bonfire` to use your checkout.  This cat command is just a short-cut so the
  instructions will be succinct.  You should open the file and paste in the name and component bits
  yourself under the `apps:` key

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

* Manually create the host-inventory-db-readonly secret

* Deploy using the following:

  ```
  bonfire deploy rhsm-subscriptions -n rhsm -i quay.io/cloudservices/rhsm-subscriptions=e1ca69b
  ```
  
  I have specifically defined the image tag here because otherwise, `bonfire` will actually run a
  `git rev-parse HEAD` and attempt to use that hash as an image tag.  Figuring out how that's meant
  to work is on the TODO list.
