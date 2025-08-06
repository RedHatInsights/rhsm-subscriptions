#!/bin/bash

# Manual Test Script for SWATCH-3545 Cascading Deduction Bug
# This script tests the theory that database connection pooling causes
# transaction isolation issues leading to incorrect deductions.

set -e

echo "🧪 SWATCH-3545 Manual Test - Database Connection Pooling Bug"
echo "============================================================"

# Configuration
SWATCH_URL="http://localhost:8002"
API_ENDPOINT="$SWATCH_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
HEADERS='-H "Content-Type: application/json" -H "x-rh-swatch-psk: placeholder"'

# Test data - same EventKey (org_id, instance_id, timestamp) for all events
ORG_ID="13259775"
INSTANCE_ID="i-test-concurrent-$(date +%s)"
TIMESTAMP="2025-07-21T17:00:00Z"

echo "📋 Test Configuration:"
echo "  - Org ID: $ORG_ID"
echo "  - Instance ID: $INSTANCE_ID"
echo "  - Timestamp: $TIMESTAMP"
echo "  - API Endpoint: $API_ENDPOINT"
echo ""

# Function to create event JSON
create_event() {
    local event_id="$1"
    local value="$2"
    cat <<EOF
[{
  "org_id": "$ORG_ID",
  "instance_id": "$INSTANCE_ID", 
  "timestamp": "$TIMESTAMP",
  "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
  "event_source": "manual-test-$event_id",
  "measurements": [{
    "metric_id": "vCPUs",
    "value": $value
  }],
  "product_tag": ["rhel-for-x86-els-payg"],
  "service_type": "RHEL System",
  "display_name": "manual-test-server-$event_id",
  "sla": "Premium",
  "usage": "Production",
  "billing_provider": "aws",
  "billing_account_id": "746157280291"
}]
EOF
}

# Function to send event and capture response
send_event() {
    local event_id="$1"
    local value="$2"
    local description="$3"
    
    echo "📤 Sending $description (value: $value)..."
    
    local payload=$(create_event "$event_id" "$value")
    local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
        -X POST "$API_ENDPOINT" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "$payload")
    
    local http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
    local body=$(echo "$response" | grep -v "HTTP_CODE:")
    
    if [ "$http_code" = "200" ]; then
        echo "✅ Success: $description sent"
    else
        echo "❌ Failed: $description (HTTP $http_code)"
        echo "Response: $body"
    fi
    
    # Small delay to see the sequence clearly
    sleep 0.1
}

# Function to send concurrent events (this is where the bug should manifest)
send_concurrent_events() {
    echo ""
    echo "🚀 CRITICAL TEST: Sending concurrent API calls to same replica"
    echo "This should trigger the database connection pooling bug..."
    echo ""
    
    # Create event payloads
    local event2_payload=$(create_event "concurrent-2" "10.0")
    local event3_payload=$(create_event "concurrent-3" "100.0")
    
    echo "📤 Sending TWO CONCURRENT API calls with same EventKey..."
    echo "  - Event 2: value 10.0 (should create deduction -1.0)"
    echo "  - Event 3: value 100.0 (should create deduction -10.0)"
    echo ""
    
    # Send both requests simultaneously in background
    {
        echo "🔄 [Background] Sending Event 2 (value: 10.0)..."
        curl -s -X POST "$API_ENDPOINT" \
            -H "Content-Type: application/json" \
            -H "x-rh-swatch-psk: placeholder" \
            -d "$event2_payload" > /tmp/event2_response.txt 2>&1
        echo "✅ [Background] Event 2 completed"
    } &
    
    {
        echo "🔄 [Background] Sending Event 3 (value: 100.0)..."
        curl -s -X POST "$API_ENDPOINT" \
            -H "Content-Type: application/json" \
            -H "x-rh-swatch-psk: placeholder" \
            -d "$event3_payload" > /tmp/event3_response.txt 2>&1
        echo "✅ [Background] Event 3 completed"
    } &
    
    # Wait for both to complete
    wait
    
    echo ""
    echo "📋 Concurrent requests completed. Responses:"
    echo "Event 2 response: $(cat /tmp/event2_response.txt)"
    echo "Event 3 response: $(cat /tmp/event3_response.txt)"
}

# Function to query database for verification
verify_results() {
    echo ""
    echo "🔍 VERIFICATION: Checking database for deduction events..."
    echo "Expected behavior:"
    echo "  - First deduction: -1.0 (deducts original value) ✅"
    echo "  - Second deduction: -10.0 (deducts most recent value) ✅"
    echo ""
    echo "Bug behavior (if present):"
    echo "  - First deduction: -1.0 (deducts original value) ✅" 
    echo "  - Second deduction: -1.0 (deducts original value) ❌ BUG!"
    echo ""
    echo "🗄️  To verify, check the database manually:"
    echo "   SELECT event_id, data->>'amendment_type' as amendment_type,"
    echo "          data->'measurements'->0->>'value' as value,"
    echo "          record_date"
    echo "   FROM events"
    echo "   WHERE org_id = '$ORG_ID'"
    echo "     AND instance_id = '$INSTANCE_ID'"
    echo "     AND timestamp = '$TIMESTAMP'"
    echo "   ORDER BY record_date;"
    echo ""
}

# Main test execution
main() {
    echo "🏁 Starting SWATCH-3545 Manual Test Sequence"
    echo ""
    
    # Test 1: Establish baseline
    echo "📍 STEP 1: Create initial event (baseline)"
    send_event "initial" "1.0" "Initial event"
    
    sleep 1
    
    # Test 2: Single sequential event (should work correctly)
    echo ""
    echo "📍 STEP 2: Send sequential event (control test)"
    send_event "sequential" "5.0" "Sequential event"
    
    sleep 1
    
    # Test 3: The critical test - concurrent events
    echo ""
    echo "📍 STEP 3: Send concurrent events (BUG TEST)"
    send_concurrent_events
    
    sleep 2
    
    # Test 4: Verification instructions
    verify_results
    
    echo ""
    echo "🎯 TEST COMPLETE!"
    echo ""
    echo "📊 To analyze results:"
    echo "1. Check swatch-tally logs for 'Resolving existing events' messages"
    echo "2. Look for 'In=X Resolved=Y' patterns"
    echo "3. Verify deduction values in database using the SQL query above"
    echo "4. If second deduction is -1.0 instead of -10.0, the bug is reproduced!"
    echo ""
    echo "🐛 Bug indicators to look for:"
    echo "   - Multiple deductions with same value (-1.0, -1.0) instead of progressive (-1.0, -10.0)"
    echo "   - Log messages showing stale reads during conflict resolution"
    echo "   - Different 'metering_batch_id' values for events with same EventKey"
}

# Cleanup function
cleanup() {
    rm -f /tmp/event2_response.txt /tmp/event3_response.txt
}

# Set up cleanup on exit
trap cleanup EXIT

# Check if swatch-tally is running
echo "🔍 Checking if swatch-tally is running on port 8002..."
if ! curl -s "$SWATCH_URL/actuator/health" > /dev/null; then
    echo "❌ ERROR: swatch-tally is not running on port 8002"
    echo ""
    echo "Please start swatch-tally first:"
    echo "SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 MANAGEMENT_SERVER_PORT=9002 SERVER_PORT=8002 DEV_MODE=true ./mvnw -pl swatch-tally spring-boot:run"
    exit 1
fi

echo "✅ swatch-tally is running"
echo ""

# Run the test
main