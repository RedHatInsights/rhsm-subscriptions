#!/bin/bash

# Kafka broker initialization script

set -e

echo "🚀 Initializing Kafka broker with broker ID: $KAFKA_BROKER_ID"

# Create logs directory
mkdir -p $KAFKA_LOG_DIRS

# Create server.properties
cat > /opt/kafka/config/server.properties << EOF
# Broker configuration
broker.id=$KAFKA_BROKER_ID

# Zookeeper configuration
zookeeper.connect=$KAFKA_ZOOKEEPER_CONNECT

# Network configuration
listeners=$KAFKA_LISTENERS
advertised.listeners=$KAFKA_ADVERTISED_LISTENERS
listener.security.protocol.map=$KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
inter.broker.listener.name=$KAFKA_INTER_BROKER_LISTENER_NAME

# Log configuration
log.dirs=$KAFKA_LOG_DIRS
log.retention.hours=168
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

# Replication configuration
default.replication.factor=$KAFKA_DEFAULT_REPLICATION_FACTOR
min.insync.replicas=$KAFKA_MIN_INSYNC_REPLICAS
num.partitions=$KAFKA_NUM_PARTITIONS
offsets.topic.replication.factor=$KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR

# Performance configuration
num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Producer/Consumer configuration
num.partitions=$KAFKA_NUM_PARTITIONS
default.replication.factor=$KAFKA_DEFAULT_REPLICATION_FACTOR
min.insync.replicas=$KAFKA_MIN_INSYNC_REPLICAS

# Monitoring
jmx.port=9999
jmx.hostname=localhost
EOF

echo "📋 Starting Kafka broker $KAFKA_BROKER_ID..."
echo "Configuration:"
echo "  - Broker ID: $KAFKA_BROKER_ID"
echo "  - Zookeeper Connect: $KAFKA_ZOOKEEPER_CONNECT"
echo "  - Listeners: $KAFKA_LISTENERS"
echo "  - Advertised Listeners: $KAFKA_ADVERTISED_LISTENERS"
echo "  - Replication Factor: $KAFKA_DEFAULT_REPLICATION_FACTOR"
echo "  - Partitions: $KAFKA_NUM_PARTITIONS"
echo "  - Log Dirs: $KAFKA_LOG_DIRS"

# Start Kafka
exec /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties 