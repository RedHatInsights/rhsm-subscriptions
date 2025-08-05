#!/bin/bash

# Test script for reproducing the cascading deductions bug using metering API
# This approach uses the metering API to create events that should conflict

echo "=== Metering API Cascading Deductions Bug Test ==="
echo

# Configuration
ORG_ID="13259775"
INSTANCE_ID="i-0d83c7a09e2589d87"
TIMESTAMP="2025-07-03T12:00:00Z"

echo "Configuration:"
echo "  Org ID: $ORG_ID"
echo "  Instance ID: $INSTANCE_ID"
echo "  Timestamp: $TIMESTAMP"
echo "  Kafka Batch Size: SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2"
echo

# Check if services are running
echo "Checking if services are running..."
if ! curl -s http://localhost:8005/health > /dev/null; then
    echo "ERROR: swatch-metrics service not running on port 8005"
    exit 1
fi

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

# Create multiple metering calls in quick succession to trigger conflicts
echo "Making multiple metering API calls in quick succession..."
echo

# Call 1: Create initial events
echo "Metering call 1..."
curl -s -X 'POST' "http://localhost:8005/api/swatch-metrics/v1/internal/metering/rhel-for-x86-els-payg?orgId=$ORG_ID&endDate=$TIMESTAMP&rangeInMinutes=60" \
  -H 'accept: */*' \
  -H 'x-rh-swatch-psk: placeholder' \
  -w "Status: %{http_code}\n" -o /dev/null

sleep 1

# Call 2: Create more events that might conflict
echo "Metering call 2..."
curl -s -X 'POST' "http://localhost:8005/api/swatch-metrics/v1/internal/metering/rhel-for-x86-els-payg?orgId=$ORG_ID&endDate=$TIMESTAMP&rangeInMinutes=60" \
  -H 'accept: */*' \
  -H 'x-rh-swatch-psk: placeholder' \
  -w "Status: %{http_code}\n" -o /dev/null

sleep 1

# Call 3: Create even more events
echo "Metering call 3..."
curl -s -X 'POST' "http://localhost:8005/api/swatch-metrics/v1/internal/metering/rhel-for-x86-els-payg?orgId=$ORG_ID&endDate=$TIMESTAMP&rangeInMinutes=60" \
  -H 'accept: */*' \
  -H 'x-rh-swatch-psk: placeholder' \
  -w "Status: %{http_code}\n" -o /dev/null

echo
echo "Metering calls completed. Waiting for processing..."
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

# Check for events with same timestamp (potential conflicts)
SAME_TIMESTAMP_COUNT=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT COUNT(*) 
FROM (
  SELECT data->>'timestamp' as timestamp
  FROM events 
  WHERE data->>'org_id' = '$ORG_ID'
    AND data->>'instance_id' = '$INSTANCE_ID'
  GROUP BY data->>'timestamp'
  HAVING COUNT(*) > 1
) t;" | tr -d ' ')

echo "Events with same timestamp (potential conflicts): $SAME_TIMESTAMP_COUNT"

if [ "$EVENT_COUNT" -gt 1 ] && [ "$SAME_TIMESTAMP_COUNT" -gt 0 ]; then
    echo "❌ BUG DETECTED: Multiple events with same timestamp found"
    echo "   This suggests the transaction isolation bug is present"
elif [ "$EVENT_COUNT" -eq 1 ]; then
    echo "✅ GOOD: Only 1 event found - conflict resolution working correctly"
    echo "   This suggests your fix is working!"
elif [ "$EVENT_COUNT" -gt 1 ] && [ "$SAME_TIMESTAMP_COUNT" -eq 0 ]; then
    echo "⚠️  Multiple events found but with different timestamps"
    echo "   This is normal behavior, not a bug"
else
    echo "⚠️  No events found - test may have failed"
fi

echo
echo "=== Expected Behavior ==="
echo "With BUG (main branch): Multiple events with same timestamp"
echo "With FIX (your branch): Single event with proper conflict resolution"
echo
echo "Test complete!" 