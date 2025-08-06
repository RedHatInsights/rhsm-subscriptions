#!/bin/bash

# Script to set up multiple swatch-tally replicas for SWATCH-3545 bug reproduction
# This creates the same multi-replica environment as production

set -e

echo "🏢 Setting up Multiple swatch-tally Replicas for SWATCH-3545 Bug Reproduction"
echo "============================================================================="

# Configuration
REPLICA_COUNT=${REPLICA_COUNT:-3}  # Match production's 3 replicas
BASE_PORT=8002
BASE_MGMT_PORT=9002

echo "📋 Multi-Replica Configuration:"
echo "  - Number of replicas: $REPLICA_COUNT"
echo "  - Base port: $BASE_PORT"
echo "  - Management base port: $BASE_MGMT_PORT"
echo "  - Connection pool per replica: DATABASE_MAX_POOL_SIZE=5"
echo ""

# Function to start a single replica
start_replica() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    local mgmt_port=$((BASE_MGMT_PORT + replica_id))
    local log_file="/tmp/swatch-tally-replica-$replica_id.log"
    
    echo "🚀 Starting Replica $replica_id:"
    echo "  - Server port: $server_port"
    echo "  - Management port: $mgmt_port"
    echo "  - Log file: $log_file"
    
    # Set environment variables for this replica
    export SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500
    export MANAGEMENT_SERVER_PORT=$mgmt_port
    export SERVER_PORT=$server_port
    export DEV_MODE=true
    export DATABASE_MAX_POOL_SIZE=5  # Limited like production
    export DATABASE_CONNECTION_TIMEOUT_MS=5000
    export KAFKA_MESSAGE_THREADS=1
    
    # Start the replica in background
    nohup ./mvnw -pl swatch-tally spring-boot:run > "$log_file" 2>&1 &
    local pid=$!
    
    echo "✅ Replica $replica_id started with PID $pid"
    echo "   Logs: tail -f $log_file"
    
    # Store PID for cleanup
    echo "$pid" > "/tmp/swatch-tally-replica-$replica_id.pid"
    
    return 0
}

# Function to check if a replica is ready
check_replica_health() {
    local replica_id="$1"
    local server_port=$((BASE_PORT + replica_id))
    
    echo "🔍 Checking Replica $replica_id health (port $server_port)..."
    
    # Try up to 30 times with 2 second intervals (60 seconds total)
    for i in {1..30}; do
        if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
           -H "Content-Type: application/json" \
           -H "x-rh-swatch-psk: placeholder" \
           -d '[]' > /dev/null 2>&1; then
            echo "✅ Replica $replica_id is ready"
            return 0
        fi
        
        if [ $i -eq 30 ]; then
            echo "❌ Replica $replica_id failed to start within 60 seconds"
            return 1
        fi
        
        echo "⏳ Waiting for Replica $replica_id... ($i/30)"
        sleep 2
    done
}

# Function to stop all replicas
stop_all_replicas() {
    echo ""
    echo "🛑 Stopping all swatch-tally replicas..."
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local pid_file="/tmp/swatch-tally-replica-$i.pid"
        
        if [[ -f "$pid_file" ]]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                echo "🛑 Stopping Replica $i (PID $pid)..."
                kill "$pid"
                rm -f "$pid_file"
            else
                echo "⚠️  Replica $i was not running"
                rm -f "$pid_file"
            fi
        fi
    done
    
    echo "✅ All replicas stopped"
}

# Function to show replica status
show_replica_status() {
    echo ""
    echo "📊 Replica Status:"
    echo "=================="
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local server_port=$((BASE_PORT + i))
        local mgmt_port=$((BASE_MGMT_PORT + i))
        local pid_file="/tmp/swatch-tally-replica-$i.pid"
        local log_file="/tmp/swatch-tally-replica-$i.log"
        
        echo "Replica $i:"
        echo "  - Server port: $server_port"
        echo "  - Management port: $mgmt_port"
        echo "  - Log file: $log_file"
        
        if [[ -f "$pid_file" ]]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                echo "  - Status: ✅ RUNNING (PID $pid)"
                
                # Test API endpoint
                if curl -s "http://localhost:$server_port/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
                   -H "Content-Type: application/json" \
                   -H "x-rh-swatch-psk: placeholder" \
                   -d '[]' > /dev/null 2>&1; then
                    echo "  - API: ✅ RESPONDING"
                else
                    echo "  - API: ❌ NOT RESPONDING"
                fi
            else
                echo "  - Status: ❌ STOPPED"
                rm -f "$pid_file"
            fi
        else
            echo "  - Status: ❌ NOT STARTED"
        fi
        echo ""
    done
}

# Function to generate multi-replica test script
generate_multi_replica_test() {
    local test_script="/home/lburnett/code/rhsm-subscriptions/test-multi-replica-concurrent-bug.sh"
    
    echo "📝 Generating multi-replica test script: $test_script"
    
    cat > "$test_script" << 'EOF'
#!/bin/bash

# Multi-replica concurrent test for SWATCH-3545 bug reproduction
# This sends the SAME EventKey to DIFFERENT replicas simultaneously

set -e

echo "🎯 Multi-Replica Concurrent Test for SWATCH-3545 Bug"
echo "===================================================="

# Configuration
REPLICA_COUNT=3
BASE_PORT=8002
ORG_ID="13259775"
TIMESTAMP="2025-07-21T17:00:00Z"
TEST_ID=$(date +%s)
CRITICAL_INSTANCE="multi-replica-critical-$TEST_ID"

echo "📋 Test Configuration:"
echo "  - Replicas: $REPLICA_COUNT"
echo "  - Critical instance: $CRITICAL_INSTANCE"
echo "  - Test ID: $TEST_ID"
echo ""

# Function to create event JSON
create_event() {
    local instance_id="$1"
    local value="$2"
    local event_source="$3"
    cat <<EOJSON
[{
  "org_id": "$ORG_ID",
  "instance_id": "$instance_id",
  "timestamp": "$TIMESTAMP",
  "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
  "event_source": "$event_source",
  "measurements": [{
    "metric_id": "vCPUs",
    "value": $value
  }],
  "product_tag": ["rhel-for-x86-els-payg"],
  "service_type": "RHEL System",
  "display_name": "multi-replica-test-$instance_id",
  "sla": "Premium",
  "usage": "Production",
  "billing_provider": "aws",
  "billing_account_id": "746157280291"
}]
EOJSON
}

# Function to send event to specific replica
send_to_replica() {
    local replica_id="$1"
    local instance_id="$2"
    local value="$3"
    local event_source="$4"
    
    local port=$((BASE_PORT + replica_id))
    local url="http://localhost:$port/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
    local payload=$(create_event "$instance_id" "$value" "$event_source")
    
    echo "📤 [Replica $replica_id] Sending event (value: $value)..."
    local start_time=$(date +%s.%3N)
    
    local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
        -X POST "$url" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "$payload")
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "scale=3; $end_time - $start_time" | bc)
    local http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
    
    if [ "$http_code" = "200" ]; then
        echo "✅ [Replica $replica_id] Success in ${duration}s"
    else
        echo "❌ [Replica $replica_id] Failed (HTTP $http_code) in ${duration}s"
    fi
}

# Main test
main() {
    echo "🏁 Starting Multi-Replica Concurrent Test"
    echo ""
    
    # Step 1: Send baseline event to replica 0
    echo "📍 STEP 1: Sending baseline event to Replica 0..."
    send_to_replica 0 "$CRITICAL_INSTANCE" "1.0" "baseline-replica-0"
    sleep 2
    
    # Step 2: Send SAME EventKey events to DIFFERENT replicas SIMULTANEOUSLY
    echo ""
    echo "📍 STEP 2: 🎯 CRITICAL TEST - Same EventKey to Different Replicas"
    echo "This should trigger the multi-replica race condition bug!"
    echo ""
    
    local pids=()
    
    # Send to replica 0
    {
        send_to_replica 0 "$CRITICAL_INSTANCE" "10.0" "concurrent-replica-0"
    } &
    pids+=($!)
    
    # Send to replica 1  
    {
        send_to_replica 1 "$CRITICAL_INSTANCE" "20.0" "concurrent-replica-1"
    } &
    pids+=($!)
    
    # Send to replica 2
    {
        send_to_replica 2 "$CRITICAL_INSTANCE" "30.0" "concurrent-replica-2"
    } &
    pids+=($!)
    
    # Wait for all concurrent requests
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    echo ""
    echo "✅ All concurrent requests completed"
    
    # Step 3: Wait and verify results
    echo ""
    echo "📍 STEP 3: Waiting for event processing..."
    sleep 5
    
    # Step 4: Check results
    echo ""
    echo "🔍 VERIFICATION: Checking for multi-replica bug..."
    
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT 
        COUNT(*) as total_events,
        COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,
        array_agg(data->'measurements'->0->>'value' ORDER BY record_date) as value_sequence,
        array_agg(data->>'event_source' ORDER BY record_date) as source_sequence
    FROM events
    WHERE org_id = '$ORG_ID'
      AND timestamp = '$TIMESTAMP'
      AND instance_id = '$CRITICAL_INSTANCE';" || echo "Database query failed"
    
    echo ""
    echo "🔍 Checking for duplicate deductions (BUG INDICATOR):"
    
    local duplicates=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT deduction_value, COUNT(*) as duplicate_count
    FROM (
      SELECT data->'measurements'->0->>'value' as deduction_value
      FROM events
      WHERE org_id = '$ORG_ID'
        AND timestamp = '$TIMESTAMP'
        AND instance_id = '$CRITICAL_INSTANCE'
        AND data->>'amendment_type' = 'deduction'
    ) deductions
    GROUP BY deduction_value
    HAVING COUNT(*) > 1;" 2>/dev/null)
    
    if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
        echo "🐛 BUG DETECTED! Duplicate deduction values found:"
        echo "$duplicates"
        echo ""
        echo "🎉 SUCCESS: SWATCH-3545 multi-replica bug reproduced!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Either the bug wasn't triggered or Kafka consumer groups prevented it"
    fi
    
    echo ""
    echo "🎯 MULTI-REPLICA TEST COMPLETE!"
}

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo "❌ ERROR: 'bc' command not found. Please install it"
    exit 1
fi

# Run the test
main
EOF

    chmod +x "$test_script"
    echo "✅ Multi-replica test script created: $test_script"
}

# Main execution
case "${1:-start}" in
    "start")
        echo "🚀 Starting $REPLICA_COUNT swatch-tally replicas..."
        echo ""
        
        # Start all replicas
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            start_replica "$i"
            sleep 2  # Stagger startup to avoid port conflicts
        done
        
        echo ""
        echo "⏳ Waiting for all replicas to be ready..."
        sleep 10
        
        # Check health of all replicas
        healthy_count=0
        for i in $(seq 0 $((REPLICA_COUNT - 1))); do
            if check_replica_health "$i"; then
                healthy_count=$((healthy_count + 1))
            fi
        done
        
        echo ""
        echo "📊 Startup Summary: $healthy_count/$REPLICA_COUNT replicas healthy"
        
        if [ $healthy_count -eq $REPLICA_COUNT ]; then
            echo "🎉 All replicas started successfully!"
            generate_multi_replica_test
            echo ""
            echo "🎯 Next steps:"
            echo "  1. Run: ./test-multi-replica-concurrent-bug.sh"
            echo "  2. Check for the SWATCH-3545 bug pattern"
            echo "  3. When done: $0 stop"
        else
            echo "⚠️  Some replicas failed to start. Check logs:"
            for i in $(seq 0 $((REPLICA_COUNT - 1))); do
                echo "     tail -f /tmp/swatch-tally-replica-$i.log"
            done
        fi
        ;;
    "stop")
        stop_all_replicas
        ;;
    "status")
        show_replica_status
        ;;
    "test")
        if [[ -f "/home/lburnett/code/rhsm-subscriptions/test-multi-replica-concurrent-bug.sh" ]]; then
            ./test-multi-replica-concurrent-bug.sh
        else
            echo "❌ Test script not found. Run '$0 start' first."
        fi
        ;;
    *)
        echo "Usage: $0 [start|stop|status|test]"
        echo ""
        echo "Commands:"
        echo "  start  - Start multiple swatch-tally replicas"
        echo "  stop   - Stop all replicas"
        echo "  status - Show replica status"
        echo "  test   - Run the multi-replica bug test"
        echo ""
        echo "Environment variables:"
        echo "  REPLICA_COUNT=3  # Number of replicas to start"
        exit 1
        ;;
esac