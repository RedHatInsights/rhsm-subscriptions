#! /bin/sh
set -e

# Set JVM options to avoid cgroups v2 issues in GitHub Actions
export KAFKA_HEAP_OPTS="-Xmx1G -Xms1G"
export KAFKA_JVM_PERFORMANCE_OPTS="-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+ExplicitGCInvokesConcurrent -XX:MaxInlineLevel=15 -Djava.awt.headless=true"
export KAFKA_JMX_OPTS="-Dcom.sun.management.jmxremote=false -Dcom.sun.management.disable=true -XX:+DisableAttachMechanism -Djdk.attach.allowAttachSelf=false"
export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:config/log4j.properties"

CLUSTER_ID=$(bin/kafka-storage.sh random-uuid)
bin/kafka-storage.sh format --ignore-formatted -t $CLUSTER_ID -c config/kraft/server.properties
bin/kafka-server-start.sh config/kraft/server.properties \
  --override advertised.listeners=${KAFKA_ADVERTISED_LISTENERS} \
  --override listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP} \
  --override listeners=${KAFKA_LISTENERS} \
  --override ssl.keystore.location=${KAFKA_SSL_KEYSTORE_LOCATION} \
  --override ssl.keystore.password=${KAFKA_SSL_KEYSTORE_PASSWORD} \
  --override ssl.client.auth=${KAFKA_SSL_CLIENT_AUTH} \
  --override sasl.enabled.mechanisms=${KAFKA_SASL_MECHANISM}
