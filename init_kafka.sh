set -e
CLUSTER_ID=$(bin/kafka-storage.sh random-uuid)
bin/kafka-storage.sh format --ignore-formatted -t $CLUSTER_ID -c config/kraft/server.properties
bin/kafka-server-start.sh config/kraft/server.properties --override advertised.listeners=${KAFKA_ADVERTISED_LISTENERS} --override listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP} --override listeners=${KAFKA_LISTENERS}
