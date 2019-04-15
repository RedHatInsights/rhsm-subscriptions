# Deploying Kafka

## Deploying via Docker

A local Kafka cluster can be deployed very quickly via docker. This
deployment includes a web interface that allows you to monitor messages
and kafka statistics. This is useful for development.

See: https://github.com/Landoop/fast-data-dev

```
docker run --rm --net host landoop/fast-data-dev
```

## Deploying to Minishift

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
