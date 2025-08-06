#!/bin/bash

# Nginx load balancer initialization script

set -e

echo "🚀 Starting Nginx load balancer for Kafka cluster..."

echo "📋 Configuration:"
echo "  - Upstream: kafka-broker-1:9092, kafka-broker-2:9092, kafka-broker-3:9092"
echo "  - Listen: 9092"
echo "  - Load balancing: round-robin"

# Start nginx
exec nginx -g "daemon off;" 