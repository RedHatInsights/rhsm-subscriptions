#!/bin/bash

# Kafka cluster initialization script for multi-broker setup
# This script sets up a 3-broker Kafka cluster with KRaft mode

set -e

echo "🚀 Initializing Kafka cluster with broker ID: $KAFKA_BROKER_ID"

# Create logs directory
mkdir -p /tmp/logs

# Generate cluster ID if this is the first broker
if [ "$KAFKA_BROKER_ID" = "1" ]; then
    echo "📋 Generating cluster ID for first broker..."
    CLUSTER_ID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
    echo "Generated cluster ID: $CLUSTER_ID"
    
    # Format storage for first broker
    echo "📋 Formatting storage for broker 1..."
    /opt/kafka/bin/kafka-storage.sh format -t $CLUSTER_ID -c /opt/kafka/config/kraft/server.properties
else
    echo "📋 Waiting for cluster ID from broker 1..."
    # Wait for broker 1 to be ready and get cluster ID
    sleep 30
    CLUSTER_ID=$(/opt/kafka/bin/kafka-cluster.sh cluster-id --bootstrap-server kafka-broker-1:9092 2>/dev/null || echo "")
    if [ -z "$CLUSTER_ID" ]; then
        echo "❌ Failed to get cluster ID from broker 1"
        exit 1
    fi
    echo "Retrieved cluster ID: $CLUSTER_ID"
    
    # Format storage for other brokers
    echo "📋 Formatting storage for broker $KAFKA_BROKER_ID..."
    /opt/kafka/bin/kafka-storage.sh format -t $CLUSTER_ID -c /opt/kafka/config/kraft/server.properties
fi

# Create server.properties for this broker
cat > /opt/kafka/config/kraft/server.properties << EOF
# Broker configuration
broker.id=$KAFKA_BROKER_ID
node.id=$KAFKA_NODE_ID

# Network configuration
listeners=PLAINTEXT://:29092,PLAINTEXT_HOST://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://kafka-broker-$KAFKA_BROKER_ID:29092,PLAINTEXT_HOST://localhost:$((9092 + ($KAFKA_BROKER_ID - 1) * 2))
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
inter.broker.listener.name=PLAINTEXT

# Controller configuration
controller.listener.names=CONTROLLER
controller.quorum.voters=1@kafka-broker-1:9093,2@kafka-broker-2:9093,3@kafka-broker-3:9093
process.roles=broker,controller

# Log configuration
log.dirs=/tmp/kafka-logs
log.retention.hours=168
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

# Replication configuration
default.replication.factor=3
min.insync.replicas=2
num.partitions=3

# Performance configuration
num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Producer/Consumer configuration
num.partitions=3
default.replication.factor=3
min.insync.replicas=2

# Security configuration (if needed)
# sasl.enabled.mechanisms=PLAIN
# sasl.mechanism.inter.broker.protocol=PLAIN

# Monitoring
jmx.port=9999
jmx.hostname=localhost
EOF

echo "📋 Starting Kafka broker $KAFKA_BROKER_ID..."
echo "Configuration:"
echo "  - Broker ID: $KAFKA_BROKER_ID"
echo "  - Node ID: $KAFKA_NODE_ID"
echo "  - Cluster ID: $CLUSTER_ID"
echo "  - Listeners: PLAINTEXT://:29092, PL AINTEXT_HOST://:9092, CONTROLLER://:9093"
echo "  - Log dirs: /tmp/kafka-logs"

# Start Kafka
exec /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties 