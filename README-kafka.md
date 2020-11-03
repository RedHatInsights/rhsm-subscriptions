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

## Configuring the KafkaTaskQueue in rhsm-conduit

rhsm-conduit can be configured to use Kafka for asynchronous inventory updates.
The following shows a basic configuration of the Kafka consumers and
producers used by rhsm-conduit when running with the KafkaTaskQueue.

```properties
#
# kafka configuration
#

# Enables the use of Kafka as a task queue
rhsm-conduit.tasks.queue=kafka

# The number of threads that will be processing messages (should match
# the number of partitions on the kafka topic)
spring.kafka.listener.concurrency=3

# A comma separated list of Kafka servers. When running in a multi-broker
# configuration, at least 2 servers should be specified.
spring.kafka.bootstrap-servers=localhost:9092

# When a connection is dropped, this is the amount of time add before the
# next reconnection attempt.
spring.kafka.consumer.properties.reconnect.backoff.ms=2000

# The max amount of time to add before the next reconnection attempt.
spring.kafka.consumer.properties.reconnect.backoff.max.ms=10000

# The amount of time to wait before giving up on a poll of a topic.
spring.kafka.consumer.properties.default.api.timeout.ms=480000
```

### Configuring rhsm-conduit to use a Schema Registry

A schema registry can be used to ensure that messages that are sent to Kafka
are in the correct format and contain the correct data. This can be configured
in the rhsm-conduit.properties configuration file as follows.

```properties
#
# Schema Registry configuration
#

# The URL of the schema registry service
spring.kafka.properties.schema.registry.url=http://localhost:8081

# Whether or not to automatically register the schema on message send.
# In a production environment this is typically set to false and the
# schema should be registered manually via the registry service's API.
spring.kafka.properties.auto.register.schemas=false

```

### Manually Registering The Schema

Knowing the URL of the schema registry, you can manually register the schema required
by rhsm-conduit as follows:

  1. Copy the following to a new file.
      ```bash
      $ cat > create_task_message_schema_payload.json <<-JSON
      {
        "schema" : "{
          \"type\": \"record\",
          \"name\": \"TaskMessage\",
          \"namespace\": \"org.candlepin.subscriptions.task.queue.kafka.message\",
          \"fields\": [
            {
              \"name\": \"groupId\",
              \"type\": {
                \"type\": \"string\"
              }
            },
            {
              \"name\": \"type\",
              \"type\": {
                \"type\": \"string\"
              }
            },
            {
              \"name\": \"args\",
              \"type\": {
                \"type\": \"map\",
                \"values\": {
                  \"type\": \"string\"
                }
              }
            }
          ]
        }"
      }
      JSON
      ```
  2. Register the new schema with the file created above.
      ```bash
      $ curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" --data "@create_task_message_schema_payload.json" http://localhost:8081/subjects/rhsm-conduit-tasks-value/versions
      ```

**NOTE**: When deploying Kafka via the docker image above, a schema registry will be
deployed alongside of Kafka.