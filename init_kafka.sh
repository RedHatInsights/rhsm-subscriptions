#! /bin/sh
set -e
CLUSTER_ID=$(bin/kafka-storage.sh random-uuid)
bin/kafka-storage.sh format --ignore-formatted -t $CLUSTER_ID -c config/kraft/server.properties
bin/kafka-server-start.sh config/kraft/server.properties \
  --override advertised.listeners=${KAFKA_ADVERTISED_LISTENERS} \
  --override listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP} \
  --override listeners=${KAFKA_LISTENERS} \
  --override ssl.keystore.location=${KAFKA_SSL_KEYSTORE_LOCATION} \
  --override ssl.keystore.password=${KAFKA_SSL_KEYSTORE_PASSWORD} \
  --override ssl.truststore.location=${KAFKA_SSL_TRUSTSTORE_LOCATION} \
  --override ssl.truststore.password=${KAFKA_SSL_TRUSTSTORE_PASSWORD} \
  --override ssl.client.auth=${KAFKA_SSL_CLIENT_AUTH}
