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