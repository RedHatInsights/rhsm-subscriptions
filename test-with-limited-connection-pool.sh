#!/bin/bash

# Script to run swatch-tally with production-like connection pool settings
# This simulates the DATABASE_MAX_POOL_SIZE=10 from production

set -e

echo "🏭 Starting swatch-tally with Production-like Connection Pool Settings"
echo "====================================================================="

# Production-like environment variables
export SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500
export MANAGEMENT_SERVER_PORT=9002
export SERVER_PORT=8002
export DEV_MODE=true

# 🎯 KEY: Limit database connection pool to match production
export DATABASE_MAX_POOL_SIZE=5  # Even smaller than production's 10 to increase pressure
export DATABASE_CONNECTION_TIMEOUT_MS=5000  # Short timeout to force contention

# Additional production-like settings
export KAFKA_MESSAGE_THREADS=1
export MEMORY_REQUEST=1200Mi
export MEMORY_LIMIT=4096Mi

echo "📋 Production-like Configuration:"
echo "  - DATABASE_MAX_POOL_SIZE: $DATABASE_MAX_POOL_SIZE (production: 10)"
echo "  - DATABASE_CONNECTION_TIMEOUT_MS: $DATABASE_CONNECTION_TIMEOUT_MS"
echo "  - KAFKA_MESSAGE_THREADS: $KAFKA_MESSAGE_THREADS"
echo "  - SERVER_PORT: $SERVER_PORT"
echo "  - MANAGEMENT_SERVER_PORT: $MANAGEMENT_SERVER_PORT"
echo ""

echo "🚀 Starting swatch-tally with limited connection pool..."
echo "This should create the same connection pool pressure as production!"
echo ""
echo "🎯 After startup, run the connection pool pressure test:"
echo "   ./simulate-connection-pool-pressure.sh"
echo ""
echo "⚠️  To stop: Ctrl+C"
echo ""

# Start swatch-tally with production-like settings
exec ./mvnw -pl swatch-tally spring-boot:run