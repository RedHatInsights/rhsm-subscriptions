#!/bin/bash

# Direct concurrent test bypassing Kafka consumer groups
# This sends events with DIFFERENT keys but SAME EventKey components simultaneously

set -e

echo "🎯 Bypass Kafka Consumer Groups - Direct Concurrent Test"
echo "======================================================="

# Configuration
REPLICA_COUNT=3
BASE_PORT=8002
ORG_ID="13259775"
TIMESTAMP="2025-07-21T17:00:00Z"
TEST_ID=$(date +%s)

# Use SAME instance_id and timestamp but DIFFERENT Kafka keys
CRITICAL_INSTANCE="bypass-kafka-critical-$TEST_ID"

echo "📋 Test Configuration:"
echo "  - Replicas: $REPLICA_COUNT"
echo "  - Critical instance: $CRITICAL_INSTANCE (same EventKey)"
echo "  - Strategy: Different Kafka message keys to force parallel processing"
echo "  - Test ID: $TEST_ID"
echo ""

# Function to create event JSON with custom Kafka key
create_event_with_key() {
    local instance_id="$1"
    local value="$2"
    local event_source="$3"
    local kafka_key="$4"  # Different Kafka key to bypass consumer groups
    
    cat <<EOJSON
{
  "key": "$kafka_key",
  "value": {
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
    "display_name": "bypass-kafka-test-$instance_id",
    "sla": "Premium",
    "usage": "Production",
    "billing_provider": "aws",
    "billing_account_id": "746157280291"
  }
}
EOJSON
}

# Function to send event directly to Kafka bridge with custom key
send_to_kafka_bridge() {
    local replica_id="$1"
    local instance_id="$2"
    local value="$3"
    local event_source="$4"
    local kafka_key="$5"
    
    local kafka_bridge_url="http://localhost:9080/topics/platform.rhsm-subscriptions.service-instance-ingress"
    local payload=$(create_event_with_key "$instance_id" "$value" "$event_source" "$kafka_key")
    
    echo "📤 [Kafka Key: $kafka_key] Sending event (value: $value)..."
    local start_time=$(date +%s.%3N)
    
    local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
        -X POST "$kafka_bridge_url" \
        -H "Content-Type: application/vnd.kafka.json.v2+json" \
        -H "Accept: application/vnd.kafka.v2+json" \
        -d "{\"records\": [$payload]}")
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "scale=3; $end_time - $start_time" | bc)
    local http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
    
    if [ "$http_code" = "200" ]; then
        echo "✅ [Kafka Key: $kafka_key] Success in ${duration}s"
    else
        echo "❌ [Kafka Key: $kafka_key] Failed (HTTP $http_code) in ${duration}s"
        echo "Response: $(echo "$response" | grep -v "HTTP_CODE:")"
    fi
}

# Function to send event to API (fallback)
send_to_api() {
    local replica_id="$1"
    local instance_id="$2"
    local value="$3"
    local event_source="$4"
    
    local port=$((BASE_PORT + replica_id))
    local url="http://localhost:$port/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
    
    local payload="[{
      \"org_id\": \"$ORG_ID\",
      \"instance_id\": \"$instance_id\",
      \"timestamp\": \"$TIMESTAMP\",
      \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
      \"event_source\": \"$event_source\",
      \"measurements\": [{
        \"metric_id\": \"vCPUs\",
        \"value\": $value
      }],
      \"product_tag\": [\"rhel-for-x86-els-payg\"],
      \"service_type\": \"RHEL System\",
      \"display_name\": \"api-test-$instance_id\",
      \"sla\": \"Premium\",
      \"usage\": \"Production\",
      \"billing_provider\": \"aws\",
      \"billing_account_id\": \"746157280291\"
    }]"
    
    echo "📤 [API Replica $replica_id] Sending event (value: $value)..."
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
        echo "✅ [API Replica $replica_id] Success in ${duration}s"
    else
        echo "❌ [API Replica $replica_id] Failed (HTTP $http_code) in ${duration}s"
    fi
}

# Main test
main() {
    echo "🏁 Starting Bypass Kafka Consumer Groups Test"
    echo ""
    
    # Step 1: Send baseline event
    echo "📍 STEP 1: Sending baseline event..."
    send_to_api 0 "$CRITICAL_INSTANCE" "1.0" "baseline-bypass"
    sleep 2
    
    # Step 2: Test different approaches
    echo ""
    echo "📍 STEP 2: Testing different bypass strategies..."
    echo ""
    
    # Strategy A: Different Kafka keys (if Kafka bridge is available)
    if curl -s http://localhost:9080 > /dev/null 2>&1; then
        echo "🎯 STRATEGY A: Different Kafka keys to force parallel processing"
        echo "This bypasses Kafka consumer group partitioning!"
        echo ""
        
        local pids=()
        
        # Send with different Kafka keys but same EventKey
        {
            send_to_kafka_bridge 0 "$CRITICAL_INSTANCE" "10.0" "kafka-bypass-1" "key-partition-0"
        } &
        pids+=($!)
        
        {
            send_to_kafka_bridge 1 "$CRITICAL_INSTANCE" "20.0" "kafka-bypass-2" "key-partition-1"
        } &
        pids+=($!)
        
        {
            send_to_kafka_bridge 2 "$CRITICAL_INSTANCE" "30.0" "kafka-bypass-3" "key-partition-2"
        } &
        pids+=($!)
        
        # Wait for all Kafka requests
        for pid in "${pids[@]}"; do
            wait "$pid"
        done
        
        echo ""
        echo "✅ Kafka bypass strategy completed"
        
    else
        echo "⚠️  Kafka bridge not available, skipping Strategy A"
    fi
    
    # Strategy B: Rapid API succession
    echo ""
    echo "🎯 STRATEGY B: Rapid API succession to different replicas"
    echo "This creates timing pressure on conflict resolution!"
    echo ""
    
    local api_pids=()
    
    # Send rapid succession to different replicas
    {
        send_to_api 0 "$CRITICAL_INSTANCE" "40.0" "rapid-api-1"
    } &
    api_pids+=($!)
    
    {
        send_to_api 1 "$CRITICAL_INSTANCE" "50.0" "rapid-api-2"
    } &
    api_pids+=($!)
    
    {
        send_to_api 2 "$CRITICAL_INSTANCE" "60.0" "rapid-api-3"
    } &
    api_pids+=($!)
    
    # Wait for all API requests
    for pid in "${api_pids[@]}"; do
        wait "$pid"
    done
    
    echo ""
    echo "✅ All bypass strategies completed"
    
    # Step 3: Wait and verify results
    echo ""
    echo "📍 STEP 3: Waiting for event processing..."
    sleep 8  # Longer wait for multiple strategies
    
    # Step 4: Check results
    echo ""
    echo "🔍 VERIFICATION: Checking for bypass-induced bug..."
    
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
        echo "🎉 SUCCESS: SWATCH-3545 bypass strategy reproduced the bug!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 The system remains robust even with bypass strategies"
    fi
    
    echo ""
    echo "📊 Final Analysis:"
    echo "  - If no bug detected: The system is very robust to race conditions"
    echo "  - Consider that the bug may require very specific production conditions"
    echo "  - The fix should still be implemented based on the production evidence"
    
    echo ""
    echo "🎯 BYPASS KAFKA TEST COMPLETE!"
}

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo "❌ ERROR: 'bc' command not found. Please install it"
    exit 1
fi

# Run the test
main