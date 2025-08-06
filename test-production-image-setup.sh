#!/bin/bash

# Script to run swatch-tally production image for SWATCH-3545 bug reproduction
# This uses the actual production container instead of Spring Boot dev tools

set -e

echo "🏭 Production Image Test Setup for SWATCH-3545 Bug Reproduction"
echo "==============================================================="

# Configuration
REPLICA_COUNT=${REPLICA_COUNT:-3}
BASE_PORT=8002
BASE_MGMT_PORT=9002
SWATCH_IMAGE="quay.io/redhat-services-prod/rh-subs-watch-tenant/swatch-tally:9cf4219"

# Database connection (adjust for your setup)
DB_HOST=${DB_HOST:-"localhost"}
DB_PORT=${DB_PORT:-"5432"}
DB_NAME=${DB_NAME:-"rhsm-subscriptions"}
DB_USER=${DB_USER:-"rhsm-subscriptions"}
DB_PASSWORD=${DB_PASSWORD:-"rhsm-subscriptions"}

# Kafka connection (adjust for your setup)
KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-"localhost:9092"}

echo "📋 Production Image Configuration:"
echo "  - Image: $SWATCH_IMAGE"
echo "  - Replicas: $REPLICA_COUNT"
echo "  - Base port: $BASE_PORT"
echo "  - Database: $DB_HOST:$DB_PORT/$DB_NAME"
echo "  - Kafka: $KAFKA_BOOTSTRAP_SERVERS"
echo ""

# Function to start a single replica using production image
start_production_replica() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    local mgmt_port=$((BASE_MGMT_PORT + replica_id))
    local container_name="swatch-tally-replica-$replica_id"
    
    echo "🚀 Starting Production Replica $replica_id:"
    echo "  - Container: $container_name"
    echo "  - Server port: $server_port"
    echo "  - Management port: $mgmt_port"
    
    # Stop existing container if running
    podman stop "$container_name" 2>/dev/null || true
    podman rm "$container_name" 2>/dev/null || true
    
    # Start production container with production-like settings
    podman run -d \
        --name "$container_name" \
        --network host \
        --memory=4g \
        --memory-reservation=1200m \
        --cpus=2 \
        -e SERVER_PORT="$server_port" \
        -e MANAGEMENT_SERVER_PORT="$mgmt_port" \
        -e DATABASE_HOST="$DB_HOST" \
        -e DATABASE_PORT="$DB_PORT" \
        -e DATABASE_DATABASE="$DB_NAME" \
        -e DATABASE_USERNAME="$DB_USER" \
        -e DATABASE_PASSWORD="$DB_PASSWORD" \
        -e DATABASE_MAX_POOL_SIZE=10 \
        -e DATABASE_CONNECTION_TIMEOUT_MS=30000 \
        -e KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \
        -e KAFKA_MESSAGE_THREADS=1 \
        -e KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS=3600000 \
        -e SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 \
        -e SPRING_PROFILES_ACTIVE="worker,api,kafka-queue" \
        -e JAVA_MAX_MEM_RATIO=85 \
        -e GC_MAX_METASPACE_SIZE=256 \
        -e LOGGING_LEVEL_ROOT=WARN \
        -e LOGGING_LEVEL_ORG_CANDLEPIN=INFO \
        -e LOGGING_LEVEL_COM_REDHAT_SWATCH=INFO \
        -e LOGGING_SHOW_SQL_QUERIES=false \
        -e DEV_MODE=false \
        -e DEVTEST_EVENT_EDITING_ENABLED=true \
        -e ENABLE_SYNCHRONOUS_OPERATIONS=true \
        "$SWATCH_IMAGE"
    
    local container_id=$(podman ps -q --filter "name=$container_name")
    if [[ -n "$container_id" ]]; then
        echo "✅ Production Replica $replica_id started (Container ID: ${container_id:0:12})"
        echo "   Logs: podman logs -f $container_name"
    else
        echo "❌ Failed to start Production Replica $replica_id"
        return 1
    fi
    
    return 0
}

# Function to check if a production replica is ready
check_production_replica_health() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    local container_name="swatch-tally-replica-$replica_id"
    
    echo "🔍 Checking Production Replica $replica_id health (port $server_port)..."
    
    # Check if container is running
    if ! podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
        echo "❌ Container $container_name is not running"
        return 1
    fi
    
    # Try up to 60 times with 2 second intervals (120 seconds total - production startup is slower)
    for i in {1..60}; do
        if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
           -H "Content-Type: application/json" \
           -H "x-rh-swatch-psk: placeholder" \
           -d '[]' > /dev/null 2>&1; then
            echo "✅ Production Replica $replica_id is ready"
            return 0
        fi
        
        if [ $i -eq 60 ]; then
            echo "❌ Production Replica $replica_id failed to start within 120 seconds"
            echo "Container logs:"
            podman logs --tail 20 "$container_name"
            return 1
        fi
        
        echo "⏳ Waiting for Production Replica $replica_id... ($i/60)"
        sleep 2
    done
}

# Function to stop all production replicas
stop_all_production_replicas() {
    echo ""
    echo "🛑 Stopping all production swatch-tally replicas..."
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local container_name="swatch-tally-replica-$i"
        
        if podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
            echo "🛑 Stopping Production Replica $i ($container_name)..."
            podman stop "$container_name"
            podman rm "$container_name"
        else
            echo "⚠️  Production Replica $i was not running"
        fi
    done
    
    echo "✅ All production replicas stopped"
}

# Function to show production replica status
show_production_replica_status() {
    echo ""
    echo "📊 Production Replica Status:"
    echo "============================="
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local server_port=$((BASE_PORT + i))
        local mgmt_port=$((BASE_MGMT_PORT + i))
        local container_name="swatch-tally-replica-$i"
        
        echo "Production Replica $i:"
        echo "  - Container: $container_name"
        echo "  - Server port: $server_port"
        echo "  - Management port: $mgmt_port"
        
        if podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
            local container_id=$(podman ps -q --filter "name=$container_name")
            echo "  - Status: ✅ RUNNING (Container ID: ${container_id:0:12})"
            
            # Test API endpoint
            if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
               -H "Content-Type: application/json" \
               -H "x-rh-swatch-psk: placeholder" \
               -d '[]' > /dev/null 2>&1; then
                echo "  - API: ✅ RESPONDING"
            else
                echo "  - API: ❌ NOT RESPONDING"
            fi
            
            # Show recent logs
            echo "  - Recent logs: podman logs --tail 5 $container_name"
        else
            echo "  - Status: ❌ NOT RUNNING"
        fi
        echo ""
    done
}

# Function to run production image bug test
run_production_bug_test() {
    echo "🎯 Running SWATCH-3545 bug test with production images..."
    
    if [[ -f "./test-multi-replica-concurrent-bug.sh" ]]; then
        echo "📋 Using existing multi-replica test script..."
        ./test-multi-replica-concurrent-bug.sh
    else
        echo "❌ Multi-replica test script not found"
        echo "💡 Create it by running the multi-replica setup first"
        return 1
    fi
}

# Main execution
case "${1:-start}" in
    "start")
        echo "🚀 Starting $REPLICA_COUNT production swatch-tally replicas..."
        echo ""
        
        # Start all production replicas
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            start_production_replica "$i"
            sleep 3  # Stagger startup to avoid resource conflicts
        done
        
        echo ""
        echo "⏳ Waiting for all production replicas to be ready..."
        sleep 15  # Production containers take longer to start
        
        # Check health of all replicas
        healthy_count=0
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            if check_production_replica_health "$i"; then
                healthy_count=$((healthy_count + 1))
            fi
        done
        
        echo ""
        echo "📊 Production Startup Summary: $healthy_count/$REPLICA_COUNT replicas healthy"
        
        if [ $healthy_count -eq $REPLICA_COUNT ]; then
            echo "🎉 All production replicas started successfully!"
            echo ""
            echo "🎯 Next steps:"
            echo "  1. Run: $0 test"
            echo "  2. Check for the SWATCH-3545 bug pattern"
            echo "  3. When done: $0 stop"
            echo ""
            echo "🔍 Key differences from dev setup:"
            echo "  - Production JVM settings and optimizations"
            echo "  - Actual production build artifacts"
            echo "  - Production-tuned connection pooling"
            echo "  - Real container resource constraints"
        else
            echo "⚠️  Some production replicas failed to start. Check logs:"
            for i in $(seq 0 $((REPLICA_COUNT - 1))); do
                echo "     podman logs swatch-tally-replica-$i"
            done
        fi
        ;;
    "stop")
        stop_all_production_replicas
        ;;
    "status")
        show_production_replica_status
        ;;
    "test")
        run_production_bug_test
        ;;
    "logs")
        local replica_id="${2:-0}"
        local container_name="swatch-tally-replica-$replica_id"
        echo "📋 Showing logs for Production Replica $replica_id:"
        podman logs -f "$container_name"
        ;;
    *)
        echo "Usage: $0 [start|stop|status|test|logs [replica_id]]"
        echo ""
        echo "Commands:"
        echo "  start       - Start production swatch-tally replicas"
        echo "  stop        - Stop all production replicas"
        echo "  status      - Show production replica status"
        echo "  test        - Run the SWATCH-3545 bug test"
        echo "  logs [N]    - Show logs for replica N (default: 0)"
        echo ""
        echo "Environment variables:"
        echo "  REPLICA_COUNT=3                    # Number of replicas"
        echo "  DB_HOST=localhost                  # Database host"
        echo "  DB_PORT=5432                       # Database port"
        echo "  KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Kafka servers"
        echo ""
        echo "🎯 This uses the PRODUCTION IMAGE instead of Spring Boot dev tools!"
        echo "   This may reproduce bugs that don't appear in development."
        exit 1
        ;;
esac