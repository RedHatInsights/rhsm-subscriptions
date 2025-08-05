#!/bin/bash

# Manual test script for reproducing the cascading deductions bug
# This script uses curl to send events directly without Python dependencies

echo "=== Manual Cascading Deductions Bug Test ==="
echo

# Configuration
ORG_ID="13259775"
INSTANCE_ID="i-test-cascade-bug-12345"
TIMESTAMP="2025-07-03T12:00:00Z"
BASE_URL="http://localhost:8003"

echo "Configuration:"
echo "  Org ID: $ORG_ID"
echo "  Instance ID: $INSTANCE_ID"
echo "  Timestamp: $TIMESTAMP"
echo "  Kafka Batch Size: SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2"
echo

# Create identity header
IDENTITY_HEADER=$(echo -n "{\"identity\":{\"account_number\":\"\",\"type\":\"User\",\"user\":{\"is_org_admin\":true},\"internal\":{\"org_id\":\"$ORG_ID\"}}}" | base64 -w 0)

# Check if services are running
echo "Checking if services are running..."
if ! curl -s http://localhost:8003/health > /dev/null; then
    echo "ERROR: swatch-tally service not running on port 8003"
    exit 1
fi

echo "Services are running."
echo

# Clear any existing events for this test instance
echo "Clearing existing events for test instance..."
psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -c "
DELETE FROM events 
WHERE data->>'org_id' = '$ORG_ID' 
  AND data->>'instance_id' = '$INSTANCE_ID';" > /dev/null 2>&1

echo "Existing events cleared."
echo

# Create and send events
echo "Sending events in quick succession to trigger Kafka batching..."
echo

# Event 1: vCPUs = 1.0
echo "Sending event 1: vCPUs = 1.0"
curl -X 'POST' "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  -H 'Content-Type: application/json' \
  -H 'x-rh-swatch-psk: placeholder' \
  -d "[{
    \"event_id\": \"cascade-event-1\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"measurements\": [
      {
        \"value\": 1.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }]" -s -w "Status: %{http_code}\n" -o /dev/null

sleep 0.1

# Event 2: vCPUs = 2.0
echo "Sending event 2: vCPUs = 2.0"
curl -X 'POST' "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  -H 'Content-Type: application/json' \
  -H 'x-rh-swatch-psk: placeholder' \
  -d "[{
    \"event_id\": \"cascade-event-2\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"measurements\": [
      {
        \"value\": 2.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }]" -s -w "Status: %{http_code}\n" -o /dev/null

sleep 0.1

# Event 3: vCPUs = 3.0
echo "Sending event 3: vCPUs = 3.0"
curl -X 'POST' "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  -H 'Content-Type: application/json' \
  -H 'x-rh-swatch-psk: placeholder' \
  -d "[{
    \"event_id\": \"cascade-event-3\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"measurements\": [
      {
        \"value\": 3.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }]" -s -w "Status: %{http_code}\n" -o /dev/null

echo
echo "Events sent. Waiting for Kafka consumer to process events..."
sleep 5

# Check the results
echo "=== Checking Database Results ==="
echo

psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -c "
SELECT 
  data->>'event_id' as event_id,
  data->>'timestamp' as timestamp,
  data->>'instance_id' as instance_id,
  data->'measurements' as measurements,
  data->>'record_date' as record_date
FROM events 
WHERE data->>'org_id' = '$ORG_ID'
  AND data->>'instance_id' = '$INSTANCE_ID'
ORDER BY data->>'record_date';"

echo
echo "=== Analysis ==="
echo

# Count events
EVENT_COUNT=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT COUNT(*) 
FROM events 
WHERE data->>'org_id' = '$ORG_ID'
  AND data->>'instance_id' = '$INSTANCE_ID';" | tr -d ' ')

echo "Total events found: $EVENT_COUNT"

if [ "$EVENT_COUNT" -eq 1 ]; then
    echo "✅ GOOD: Only 1 event found - conflict resolution working correctly"
    echo "   This suggests your fix is working!"
elif [ "$EVENT_COUNT" -gt 1 ]; then
    echo "❌ BUG DETECTED: Multiple events found - cascading deductions bug present"
    echo "   This suggests the transaction isolation bug is still present"
    echo "   Expected: 1 event, Found: $EVENT_COUNT events"
else
    echo "⚠️  No events found - test may have failed"
fi

echo
echo "=== Expected Behavior ==="
echo "With BUG (main branch): Multiple events with cascading deductions"
echo "With FIX (your branch): Single event with proper conflict resolution"
echo
echo "Test complete!" 