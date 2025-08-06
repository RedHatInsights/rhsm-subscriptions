#!/bin/bash

# Test script with EXACT production configuration from stage environment
# This uses the exact same settings as the actual production pods

set -e

echo "🏭 EXACT Production Configuration Test for SWATCH-3545 Bug"
echo "=========================================================="

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

echo "📋 EXACT Production Configuration:"
echo "  - Image: $SWATCH_IMAGE"
echo "  - Replicas: $REPLICA_COUNT"
echo "  - Memory: 1200Mi requests, 4Gi limits (JAVA_MAX_MEM_RATIO=85 = ~3.4GB heap)"
echo "  - CPU: 2 cores requests/limits"
echo "  - Database connection timeout: 30000ms (30 seconds!)"
echo "  - Database max pool size: 10"
echo "  - Kafka max poll interval: 3600000ms (1 hour!)"
echo "  - GC metaspace: 256MB"
echo ""

# Function to start a single replica with EXACT production settings
start_exact_production_replica() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    local mgmt_port=$((BASE_MGMT_PORT + replica_id))
    local container_name="swatch-tally-exact-$replica_id"
    
    echo "🚀 Starting EXACT Production Replica $replica_id:"
    echo "  - Container: $container_name"
    echo "  - Server port: $server_port"
    echo "  - Management port: $mgmt_port"
    echo "  - Memory pressure: 1200Mi-4Gi with 85% heap ratio"
    echo "  - Database timeout: 30 seconds (vs our previous 5s)"
    
    # Stop existing container if running
    podman stop "$container_name" 2>/dev/null || true
    podman rm "$container_name" 2>/dev/null || true
    
    # Create logs directory for this replica
    mkdir -p "/tmp/swatch-logs-$replica_id"
    
    # Start production container with EXACT production settings
    podman run -d \
        --name "$container_name" \
        --network host \
        --memory=4g \
        --memory-reservation=1200m \
        --cpus=2 \
        -v "/tmp/swatch-logs-$replica_id:/logs" \
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
        -e SERVICE_INSTANCE_INGRESS_KAFKA_SEEK_OVERRIDE_END=false \
        -e SERVICE_INSTANCE_INGRESS_KAFKA_SEEK_OVERRIDE_TIMESTAMP="" \
        -e KAFKA_SEEK_OVERRIDE_END=false \
        -e KAFKA_SEEK_OVERRIDE_TIMESTAMP="" \
        -e SPRING_PROFILES_ACTIVE="worker,api,kafka-queue" \
        -e LOGGING_CONFIG="" \
        -e JAVA_MAX_MEM_RATIO=85 \
        -e GC_MAX_METASPACE_SIZE=256 \
        -e LOGGING_LEVEL_ROOT=WARN \
        -e LOGGING_LEVEL_ORG_CANDLEPIN=INFO \
        -e LOGGING_LEVEL_COM_REDHAT_SWATCH=INFO \
        -e LOGGING_SHOW_SQL_QUERIES=false \
        -e DEV_MODE=false \
        -e ENABLE_ACCOUNT_RESET=true \
        -e DEVTEST_EVENT_EDITING_ENABLED=true \
        -e ENABLE_SYNCHRONOUS_OPERATIONS=true \
        -e TALLY_MAX_HBI_ACCOUNT_SIZE=2147483647 \
        -e HOURLY_TALLY_EVENT_BATCH_SIZE=16000 \
        -e HBI_RECONCILIATION_FLUSH_INTERVAL=1024 \
        -e USE_CPU_SYSTEM_FACTS_TO_ALL_PRODUCTS=true \
        -e HOST_LAST_SYNC_THRESHOLD=30h \
        -e EVENT_RECORD_RETENTION=P6M \
        -e INVENTORY_DATABASE_HOST="$DB_HOST" \
        -e INVENTORY_DATABASE_DATABASE="$DB_NAME" \
        -e INVENTORY_DATABASE_USERNAME="$DB_USER" \
        -e INVENTORY_DATABASE_PASSWORD="$DB_PASSWORD" \
        -e INVENTORY_DATABASE_CONNECTION_TIMEOUT_MS=30000 \
        -e INVENTORY_DATABASE_MAX_POOL_SIZE=3 \
        -e INVENTORY_DATABASE_SCHEMA=hbi \
        -e USER_HOST=user.stage.api.redhat.com \
        -e USER_MAX_CONNECTIONS=100 \
        -e USER_MAX_ATTEMPTS=10 \
        -e USER_BACK_OFF_MAX_INTERVAL=64s \
        -e USER_BACK_OFF_INITIAL_INTERVAL=1s \
        -e USER_BACK_OFF_MULTIPLIER=2 \
        -e OPENSHIFT_BILLING_MODEL_FILTER=marketplace \
        -e PROM_URL=http://localhost:8082 \
        -e OTEL_JAVAAGENT_ENABLED=false \
        -e ENABLE_SPLUNK_HEC=false \
        "$SWATCH_IMAGE"
    
    local container_id=$(podman ps -q --filter "name=$container_name")
    if [[ -n "$container_id" ]]; then
        echo "✅ EXACT Production Replica $replica_id started (Container ID: ${container_id:0:12})"
        echo "   Memory: $(podman inspect $container_name | grep -A 5 Memory | grep Memory | head -1)"
        echo "   Logs: podman logs -f $container_name"
    else
        echo "❌ Failed to start EXACT Production Replica $replica_id"
        return 1
    fi
    
    return 0
}

# Function to check if a replica is ready
check_exact_replica_health() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    local container_name="swatch-tally-exact-$replica_id"
    
    echo "🔍 Checking EXACT Production Replica $replica_id health (port $server_port)..."
    
    # Check if container is running
    if ! podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
        echo "❌ Container $container_name is not running"
        return 1
    fi
    
    # Try up to 90 times with 2 second intervals (3 minutes total - production startup with memory pressure is slower)
    for i in {1..90}; do
        if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
           -H "Content-Type: application/json" \
           -H "x-rh-swatch-psk: placeholder" \
           -d '[]' > /dev/null 2>&1; then
            echo "✅ EXACT Production Replica $replica_id is ready"
            return 0
        fi
        
        if [ $i -eq 90 ]; then
            echo "❌ EXACT Production Replica $replica_id failed to start within 3 minutes"
            echo "Container logs:"
            podman logs --tail 20 "$container_name"
            return 1
        fi
        
        if [ $((i % 15)) -eq 0 ]; then
            echo "⏳ Waiting for EXACT Production Replica $replica_id... ($i/90) - Memory pressure startup is slow"
        fi
        sleep 2
    done
}

# Function to stop all exact production replicas
stop_all_exact_replicas() {
    echo ""
    echo "🛑 Stopping all EXACT production swatch-tally replicas..."
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local container_name="swatch-tally-exact-$i"
        
        if podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
            echo "🛑 Stopping EXACT Production Replica $i ($container_name)..."
            podman stop "$container_name"
            podman rm "$container_name"
        else
            echo "⚠️  EXACT Production Replica $i was not running"
        fi
    done
    
    echo "✅ All EXACT production replicas stopped"
}

# Function to show exact replica status with memory info
show_exact_replica_status() {
    echo ""
    echo "📊 EXACT Production Replica Status:"
    echo "==================================="
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local server_port=$((BASE_PORT + i))
        local mgmt_port=$((BASE_MGMT_PORT + i))
        local container_name="swatch-tally-exact-$i"
        
        echo "EXACT Production Replica $i:"
        echo "  - Container: $container_name"
        echo "  - Server port: $server_port"
        echo "  - Management port: $mgmt_port"
        
        if podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
            local container_id=$(podman ps -q --filter "name=$container_name")
            echo "  - Status: ✅ RUNNING (Container ID: ${container_id:0:12})"
            
            # Get memory stats
            local memory_stats=$(podman stats --no-stream --format "{{.MemUsage}}" "$container_name" 2>/dev/null || echo "N/A")
            echo "  - Memory usage: $memory_stats"
            
            # Test API endpoint
            if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
               -H "Content-Type: application/json" \
               -H "x-rh-swatch-psk: placeholder" \
               -d '[]' > /dev/null 2>&1; then
                echo "  - API: ✅ RESPONDING"
            else
                echo "  - API: ❌ NOT RESPONDING"
            fi
            
            # Show JVM info from logs
            local jvm_info=$(podman logs "$container_name" 2>/dev/null | grep -E "(heap|memory|gc)" | tail -2 | head -1 || echo "No JVM info")
            echo "  - JVM: $jvm_info"
        else
            echo "  - Status: ❌ NOT RUNNING"
        fi
        echo ""
    done
}

# Function to run production configuration bug test
run_exact_production_bug_test() {
    echo "🎯 Running SWATCH-3545 bug test with EXACT production configuration..."
    echo "This includes:"
    echo "  - 30-second database connection timeout (vs our previous 5s)"
    echo "  - 3.4GB heap memory pressure (vs unlimited local)"
    echo "  - 10 connection pool size (vs our previous 5)"
    echo "  - 1-hour Kafka poll interval (vs default short)"
    echo "  - All exact production environment variables"
    echo ""
    
    # Run our most aggressive test
    if [[ -f "./test-bypass-kafka-concurrent.sh" ]]; then
        echo "📋 Running bypass Kafka test with exact production config..."
        ./test-bypass-kafka-concurrent.sh
    fi
    
    echo ""
    echo "📋 Running rapid-fire same EventKey test..."
    
    # Custom rapid-fire test for exact production config
    local org_id="13259775"
    local timestamp="2025-07-21T17:00:00Z"
    local test_id=$(date +%s)
    local critical_instance="exact-prod-critical-$test_id"
    
    # Send baseline
    echo "📤 Sending baseline event..."
    curl -s -X POST "http://localhost:8002/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "[{
            \"org_id\": \"$org_id\",
            \"instance_id\": \"$critical_instance\",
            \"timestamp\": \"$timestamp\",
            \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
            \"event_source\": \"exact-prod-baseline\",
            \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": 1.0}],
            \"product_tag\": [\"rhel-for-x86-els-payg\"],
            \"service_type\": \"RHEL System\",
            \"display_name\": \"exact-prod-test\",
            \"sla\": \"Premium\",
            \"usage\": \"Production\",
            \"billing_provider\": \"aws\",
            \"billing_account_id\": \"746157280291\"
        }]" > /dev/null
    
    sleep 2
    
    # Rapid-fire to different replicas with same EventKey
    echo "📤 Sending 20 rapid-fire events with same EventKey to different replicas..."
    local pids=()
    
    for i in {1..20}; do
        local replica_id=$((i % 3))
        local port=$((8002 + replica_id))
        local value=$(echo "scale=1; $i * 5" | bc)
        
        {
            curl -s -X POST "http://localhost:$port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "[{
                    \"org_id\": \"$org_id\",
                    \"instance_id\": \"$critical_instance\",
                    \"timestamp\": \"$timestamp\",
                    \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
                    \"event_source\": \"exact-prod-rapid-$i\",
                    \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": $value}],
                    \"product_tag\": [\"rhel-for-x86-els-payg\"],
                    \"service_type\": \"RHEL System\",
                    \"display_name\": \"exact-prod-test\",
                    \"sla\": \"Premium\",
                    \"usage\": \"Production\",
                    \"billing_provider\": \"aws\",
                    \"billing_account_id\": \"746157280291\"
                }]" > /dev/null 2>&1
        } &
        pids+=($!)
        
        # Very small delay to create timing pressure but leverage the 30s connection timeout
        sleep 0.01
    done
    
    # Wait for all requests
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    
    echo "✅ Rapid-fire test completed"
    
    # Wait for processing
    echo "⏳ Waiting 10 seconds for event processing with 30s connection timeout..."
    sleep 10
    
    # Check results
    echo ""
    echo "🔍 VERIFICATION: Checking for EXACT production config bug..."
    
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT 
        COUNT(*) as total_events,
        COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,
        COUNT(DISTINCT data->'measurements'->0->>'value') as unique_values
    FROM events
    WHERE org_id = '$org_id'
      AND timestamp = '$timestamp'
      AND instance_id = '$critical_instance';" || echo "Database query failed"
    
    echo ""
    echo "🔍 Checking for duplicate deductions (BUG INDICATOR):"
    
    local duplicates=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT deduction_value, COUNT(*) as duplicate_count
    FROM (
      SELECT data->'measurements'->0->>'value' as deduction_value
      FROM events
      WHERE org_id = '$org_id'
        AND timestamp = '$timestamp'
        AND instance_id = '$critical_instance'
        AND data->>'amendment_type' = 'deduction'
    ) deductions
    GROUP BY deduction_value
    HAVING COUNT(*) > 1;" 2>/dev/null)
    
    if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
        echo "🐛 BUG DETECTED! Duplicate deduction values found:"
        echo "$duplicates"
        echo ""
        echo "🎉 SUCCESS: SWATCH-3545 bug reproduced with EXACT production config!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Even exact production config didn't reproduce the bug locally"
    fi
}

# Main execution
case "${1:-start}" in
    "start")
        echo "🚀 Starting $REPLICA_COUNT swatch-tally replicas with EXACT production config..."
        echo ""
        
        # Start all replicas
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            start_exact_production_replica "$i"
            sleep 5  # Longer delay for memory pressure startup
        done
        
        echo ""
        echo "⏳ Waiting for all EXACT production replicas to be ready..."
        echo "This takes longer due to memory pressure and 30s connection timeouts..."
        sleep 20
        
        # Check health of all replicas
        healthy_count=0
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            if check_exact_replica_health "$i"; then
                healthy_count=$((healthy_count + 1))
            fi
        done
        
        echo ""
        echo "📊 EXACT Production Startup Summary: $healthy_count/$REPLICA_COUNT replicas healthy"
        
        if [ $healthy_count -eq $REPLICA_COUNT ]; then
            echo "🎉 All EXACT production replicas started successfully!"
            echo ""
            echo "🎯 Next steps:"
            echo "  1. Run: $0 test"
            echo "  2. Check for the SWATCH-3545 bug pattern"
            echo "  3. When done: $0 stop"
            echo ""
            echo "🔍 Key EXACT production differences:"
            echo "  - 30-second database connection timeout (critical!)"
            echo "  - 3.4GB heap memory pressure"
            echo "  - 10 connection pool size"
            echo "  - 1-hour Kafka poll interval"
            echo "  - All production environment variables"
        else
            echo "⚠️  Some EXACT production replicas failed to start. Check logs:"
            for i in $(seq 0 $((REPLICA_COUNT - 1))); do
                echo "     podman logs swatch-tally-exact-$i"
            done
        fi
        ;;
    "stop")
        stop_all_exact_replicas
        ;;
    "status")
        show_exact_replica_status
        ;;
    "test")
        run_exact_production_bug_test
        ;;
    "logs")
        local replica_id="${2:-0}"
        local container_name="swatch-tally-exact-$replica_id"
        echo "📋 Showing logs for EXACT Production Replica $replica_id:"
        podman logs -f "$container_name"
        ;;
    *)
        echo "Usage: $0 [start|stop|status|test|logs [replica_id]]"
        echo ""
        echo "Commands:"
        echo "  start       - Start EXACT production swatch-tally replicas"
        echo "  stop        - Stop all EXACT production replicas"
        echo "  status      - Show EXACT production replica status"
        echo "  test        - Run the SWATCH-3545 bug test"
        echo "  logs [N]    - Show logs for replica N (default: 0)"
        echo ""
        echo "🎯 This uses the EXACT production configuration discovered from stage!"
        echo "   Key differences: 30s DB timeout, 3.4GB heap, production env vars"
        exit 1
        ;;
esac