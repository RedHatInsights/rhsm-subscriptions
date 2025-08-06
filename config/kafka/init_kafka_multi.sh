#!/bin/bash

# Multi-broker Kafka initialization script
# This script starts both Zookeeper and Kafka for each broker

set -e

echo "🚀 Initializing multi-broker Kafka with broker ID: $KAFKA_BROKER_ID"

# Create directories
mkdir -p /tmp/kafka-logs
mkdir -p /tmp/zookeeper

# Start Zookeeper in background
echo "📋 Starting Zookeeper for broker $KAFKA_BROKER_ID..."
/opt/kafka/bin/zookeeper-server-start.sh -daemon /opt/kafka/config/zookeeper.properties

# Wait for Zookeeper to start
sleep 10

# Start Kafka
echo "📋 Starting Kafka broker $KAFKA_BROKER_ID..."
echo "Configuration:"
echo "  - Broker ID: $KAFKA_BROKER_ID"
echo "  - Zookeeper Connect: $KAFKA_ZOOKEEPER_CONNECT"
echo "  - Listeners: $KAFKA_LISTENERS"
echo "  - Advertised Listeners: $KAFKA_ADVERTISED_LISTENERS"
echo "  - Replication Factor: $KAFKA_DEFAULT_REPLICATION_FACTOR"
echo "  - Partitions: $KAFKA_NUM_PARTITIONS"

# Start Kafka
exec /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties 