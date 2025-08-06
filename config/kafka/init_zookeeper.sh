#!/bin/bash

# Zookeeper ensemble initialization script

set -e

echo "🚀 Initializing Zookeeper ensemble with server ID: $ZOOKEEPER_SERVER_ID"

# Create data directory
mkdir -p $ZOOKEEPER_DATA_DIR

# Create myid file
echo $ZOOKEEPER_SERVER_ID > $ZOOKEEPER_DATA_DIR/myid

# Create zookeeper.properties
cat > /opt/kafka/config/zookeeper.properties << EOF
# Zookeeper configuration
dataDir=$ZOOKEEPER_DATA_DIR
clientPort=$ZOOKEEPER_CLIENT_PORT

# Ensemble configuration
initLimit=5
syncLimit=2

# Server configuration
server.1=zookeeper-1:2888:3888
server.2=zookeeper-2:2888:3888
server.3=zookeeper-3:2888:3888

# Logging
4lw.commands.whitelist=*
EOF

echo "📋 Starting Zookeeper server $ZOOKEEPER_SERVER_ID..."
echo "Configuration:"
echo "  - Server ID: $ZOOKEEPER_SERVER_ID"
echo "  - Data Dir: $ZOOKEEPER_DATA_DIR"
echo "  - Client Port: $ZOOKEEPER_CLIENT_PORT"
echo "  - Ensemble: $ZOOKEEPER_SERVERS"

# Start Zookeeper
exec /opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties 