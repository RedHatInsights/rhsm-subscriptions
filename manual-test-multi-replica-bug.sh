#!/bin/bash

# Advanced Manual Test Script for SWATCH-3545 - Multi-Replica Testing
# This script can test both single replica (connection pooling) and 
# multi-replica scenarios for the cascading deduction bug.

set -e

echo "🧪 SWATCH-3545 Advanced Manual Test - Multi-Replica & Connection Pooling"
echo "========================================================================"

# Configuration - can be overridden with environment variables
REPLICA_PORTS=${REPLICA_PORTS:-"8002"}  # Space-separated list: "8002 8003 8004"
BASE_URL_TEMPLATE=${BASE_URL_TEMPLATE:-"http://localhost"}
TEST_DURATION=${TEST_DURATION:-30}  # seconds
CONCURRENT_REQUESTS=${CONCURRENT_REQUESTS:-5}  # per replica

# Parse replica ports
IFS=' ' read -ra PORTS <<< "$REPLICA_PORTS"
REPLICA_COUNT=${#PORTS[@]}

echo "📋 Test Configuration:"
echo "  - Replica ports: ${PORTS[*]}"
echo "  - Replica count: $REPLICA_COUNT"
echo "  - Concurrent requests per replica: $CONCURRENT_REQUESTS"
echo "  - Test duration: ${TEST_DURATION}s"
echo ""

# Test data
ORG_ID="13259775"
TIMESTAMP="2025-07-21T17:00:00Z"
TEST_ID=$(date +%s)

# Function to create event JSON
create_event() {
    local instance_id="$1"
    local value="$2"
    local event_source="$3"
    cat <<EOF
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
  "display_name": "test-server-$instance_id",
  "sla": "Premium",
  "usage": "Production",
  "billing_provider": "aws",
  "billing_account_id": "746157280291"
}]
EOF
}

# Function to send event to specific replica
send_event_to_replica() {
    local port="$1"
    local instance_id="$2"
    local value="$3"
    local request_id="$4"
    
    local url="$BASE_URL_TEMPLATE:$port/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
    local payload=$(create_event "$instance_id" "$value" "replica-$port-req-$request_id")
    
    local start_time=$(date +%s.%3N)
    local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
        -X POST "$url" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "$payload" 2>/dev/null)
    local end_time=$(date +%s.%3N)
    
    local http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
    local duration=$(echo "scale=3; $end_time - $start_time" | bc)
    
    if [ "$http_code" = "200" ]; then
        echo "✅ [Replica :$port] Request $request_id: instance=$instance_id, value=$value, duration=${duration}s"
    else
        echo "❌ [Replica :$port] Request $request_id FAILED: HTTP $http_code, duration=${duration}s"
    fi
}

# Function to test single replica connection pooling
test_single_replica_pooling() {
    local port="$1"
    echo ""
    echo "🔧 Testing Single Replica Connection Pooling (Port $port)"
    echo "========================================================="
    echo "This tests the theory that multiple concurrent API calls to the SAME replica"
    echo "cause database connection pooling issues leading to stale reads."
    echo ""
    
    local instance_id="single-replica-test-$TEST_ID"
    
    # Step 1: Create baseline event
    echo "📍 Step 1: Creating baseline event..."
    send_event_to_replica "$port" "$instance_id" "1.0" "baseline"
    sleep 1
    
    # Step 2: Send multiple concurrent requests to same replica
    echo ""
    echo "📍 Step 2: Sending $CONCURRENT_REQUESTS concurrent requests to same replica..."
    echo "🎯 This should trigger connection pooling transaction isolation bug!"
    echo ""
    
    local pids=()
    for i in $(seq 1 $CONCURRENT_REQUESTS); do
        {
            local value=$(echo "scale=1; $i * 10" | bc)
            send_event_to_replica "$port" "$instance_id" "$value" "concurrent-$i"
        } &
        pids+=($!)
    done
    
    # Wait for all concurrent requests to complete
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    echo ""
    echo "🔍 Single replica test completed. Check database for instance: $instance_id"
    echo "Expected: Progressive deductions (-1.0, -10.0, -20.0, -30.0, -40.0)"
    echo "Bug: All deductions are -1.0 (original value)"
}

# Function to test multi-replica scenario  
test_multi_replica() {
    if [ $REPLICA_COUNT -lt 2 ]; then
        echo "⚠️  Skipping multi-replica test (only 1 replica configured)"
        return
    fi
    
    echo ""
    echo "🏢 Testing Multi-Replica Scenario"
    echo "================================="
    echo "This tests if multiple replicas can process events with the same EventKey"
    echo "even though Kafka consumer groups should prevent this."
    echo ""
    
    local instance_id="multi-replica-test-$TEST_ID"
    
    # Send same EventKey to different replicas simultaneously
    echo "📍 Sending events with SAME EventKey to different replicas..."
    echo "🎯 Consumer groups should prevent this, but let's verify!"
    echo ""
    
    local pids=()
    for i in "${!PORTS[@]}"; do
        local port="${PORTS[$i]}"
        local value=$(echo "scale=1; ($i + 1) * 100" | bc)
        {
            send_event_to_replica "$port" "$instance_id" "$value" "multi-replica-$i"
        } &
        pids+=($!)
    done
    
    # Wait for all requests
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    echo ""
    echo "🔍 Multi-replica test completed. Check database for instance: $instance_id"
    echo "Expected: Only ONE event should be processed (due to consumer groups)"
    echo "If multiple events: Consumer group configuration issue"
}

# Function to stress test with continuous load
stress_test() {
    local duration="$1"
    echo ""
    echo "💪 Stress Test - Continuous Load for ${duration}s"
    echo "==============================================="
    echo "This continuously sends events to all replicas to increase the chance"
    echo "of triggering race conditions and connection pooling issues."
    echo ""
    
    local end_time=$(($(date +%s) + duration))
    local request_counter=0
    
    while [ $(date +%s) -lt $end_time ]; do
        for port in "${PORTS[@]}"; do
            local instance_id="stress-test-$TEST_ID-$request_counter"
            local value=$(echo "scale=2; $request_counter * 0.5" | bc)
            {
                send_event_to_replica "$port" "$instance_id" "$value" "stress-$request_counter" 2>/dev/null
            } &
        done
        
        request_counter=$((request_counter + 1))
        
        # Limit concurrent processes to avoid overwhelming the system
        if [ $((request_counter % 10)) -eq 0 ]; then
            wait  # Wait for current batch to complete
            echo "📊 Stress test progress: $request_counter requests sent..."
        fi
        
        sleep 0.1
    done
    
    wait  # Wait for all remaining requests
    echo "✅ Stress test completed: $request_counter total requests"
}

# Function to check replica health
check_replicas() {
    echo "🔍 Checking replica health..."
    local healthy_count=0
    
    for port in "${PORTS[@]}"; do
        local health_url="$BASE_URL_TEMPLATE:$port/actuator/health"
        if curl -s "$health_url" > /dev/null 2>&1; then
            echo "✅ Replica :$port is healthy"
            healthy_count=$((healthy_count + 1))
        else
            echo "❌ Replica :$port is NOT responding"
        fi
    done
    
    echo "📊 Healthy replicas: $healthy_count/$REPLICA_COUNT"
    
    if [ $healthy_count -eq 0 ]; then
        echo ""
        echo "❌ ERROR: No healthy replicas found!"
        echo "Please start swatch-tally instances on the configured ports:"
        for port in "${PORTS[@]}"; do
            echo "  SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 MANAGEMENT_SERVER_PORT=$((port + 1000)) SERVER_PORT=$port DEV_MODE=true ./mvnw -pl swatch-tally spring-boot:run"
        done
        exit 1
    fi
    
    echo ""
}

# Function to show database verification queries
show_verification_queries() {
    echo ""
    echo "🗄️  DATABASE VERIFICATION QUERIES"
    echo "================================="
    echo ""
    echo "1. Check all events for this test run:"
    echo "   SELECT instance_id, data->>'amendment_type' as amendment_type,"
    echo "          data->'measurements'->0->>'value' as value,"
    echo "          data->>'event_source' as event_source,"
    echo "          record_date"
    echo "   FROM events"
    echo "   WHERE org_id = '$ORG_ID'"
    echo "     AND timestamp = '$TIMESTAMP'"
    echo "     AND (instance_id LIKE '%$TEST_ID%' OR data->>'event_source' LIKE '%$TEST_ID%')"
    echo "   ORDER BY instance_id, record_date;"
    echo ""
    echo "2. Check for cascading deduction bug pattern:"
    echo "   SELECT instance_id,"
    echo "          COUNT(*) as total_events,"
    echo "          COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,"
    echo "          array_agg(data->'measurements'->0->>'value' ORDER BY record_date) as value_sequence"
    echo "   FROM events"
    echo "   WHERE org_id = '$ORG_ID'"
    echo "     AND timestamp = '$TIMESTAMP'"
    echo "     AND (instance_id LIKE '%$TEST_ID%' OR data->>'event_source' LIKE '%$TEST_ID%')"
    echo "   GROUP BY instance_id"
    echo "   HAVING COUNT(*) > 2;"  # Only show instances with conflicts
    echo ""
    echo "3. Look for duplicate deduction values (BUG INDICATOR):"
    echo "   SELECT instance_id, deduction_value, COUNT(*) as duplicate_count"
    echo "   FROM ("
    echo "     SELECT instance_id, data->'measurements'->0->>'value' as deduction_value"
    echo "     FROM events"
    echo "     WHERE org_id = '$ORG_ID'"
    echo "       AND timestamp = '$TIMESTAMP'"
    echo "       AND data->>'amendment_type' = 'deduction'"
    echo "       AND (instance_id LIKE '%$TEST_ID%' OR data->>'event_source' LIKE '%$TEST_ID%')"
    echo "   ) deductions"
    echo "   GROUP BY instance_id, deduction_value"
    echo "   HAVING COUNT(*) > 1;"  # Show duplicate deduction values
    echo ""
}

# Main execution
main() {
    echo "🏁 Starting SWATCH-3545 Advanced Manual Test"
    echo ""
    
    # Check replica health
    check_replicas
    
    # Test 1: Single replica connection pooling (primary theory)
    test_single_replica_pooling "${PORTS[0]}"
    
    # Test 2: Multi-replica scenario (if multiple replicas available)
    test_multi_replica
    
    # Test 3: Stress test (optional, based on duration)
    if [ $TEST_DURATION -gt 0 ]; then
        stress_test "$TEST_DURATION"
    fi
    
    # Show verification queries
    show_verification_queries
    
    echo ""
    echo "🎯 TEST SUITE COMPLETE!"
    echo ""
    echo "🐛 Bug indicators to look for:"
    echo "   1. Multiple deductions with same value (e.g., -1.0, -1.0, -1.0)"
    echo "   2. Deductions not following progressive pattern (should be -1.0, -10.0, -20.0, etc.)"
    echo "   3. Log messages showing 'stale read' or transaction isolation issues"
    echo "   4. Events with same EventKey but different metering_batch_id values"
    echo ""
    echo "📊 Next steps:"
    echo "   1. Run the database queries above to analyze results"
    echo "   2. Check swatch-tally logs for conflict resolution messages"
    echo "   3. Look for patterns in the data that match the bug description"
    echo ""
}

# Check for bc command (needed for decimal arithmetic)
if ! command -v bc &> /dev/null; then
    echo "❌ ERROR: 'bc' command not found. Please install it:"
    echo "   sudo dnf install bc"  # Fedora/RHEL
    echo "   sudo apt install bc"  # Ubuntu/Debian
    exit 1
fi

# Show usage if help requested
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0"
    echo ""
    echo "Environment variables:"
    echo "  REPLICA_PORTS=\"8002 8003 8004\"  # Space-separated list of replica ports"
    echo "  TEST_DURATION=30                  # Stress test duration in seconds (0 to skip)"
    echo "  CONCURRENT_REQUESTS=5             # Concurrent requests per replica"
    echo ""
    echo "Examples:"
    echo "  # Test single replica:"
    echo "  ./manual-test-multi-replica-bug.sh"
    echo ""
    echo "  # Test multiple replicas:"
    echo "  REPLICA_PORTS=\"8002 8003 8004\" ./manual-test-multi-replica-bug.sh"
    echo ""
    echo "  # Quick test without stress testing:"
    echo "  TEST_DURATION=0 ./manual-test-multi-replica-bug.sh"
    exit 0
fi

# Run the test suite
main