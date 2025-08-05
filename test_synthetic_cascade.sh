#!/bin/bash

# Test script for reproducing the cascading deductions bug using synthetic data
# This creates multiple events with same instance_id and timestamp but different values

echo "=== Synthetic Data Cascading Deductions Bug Test ==="
echo

# Configuration
ORG_ID="13259775"
INSTANCE_ID="i-synthetic-cascade-test-12345"
TIMESTAMP="2025-07-03T12:00:00Z"

echo "Configuration:"
echo "  Org ID: $ORG_ID"
echo "  Instance ID: $INSTANCE_ID"
echo "  Timestamp: $TIMESTAMP"
echo "  Kafka Batch Size: SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=2"
echo

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

# Create synthetic events with same instance_id and timestamp but different values
echo "Creating synthetic events with conflicts..."
echo

# Event 1: vCPUs = 1.0
echo "Creating event 1: vCPUs = 1.0"
psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -c "
INSERT INTO events (event_id, timestamp, event_type, event_source, instance_id, org_id, data) VALUES (
  gen_random_uuid(),
  '$TIMESTAMP'::timestamp with time zone,
  'snapshot',
  'rhelemeter',
  '$INSTANCE_ID',
  '$ORG_ID',
  '{
    \"event_id\": \"synthetic-event-1\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"record_date\": \"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",
    \"measurements\": [
      {
        \"value\": 1.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }'
);"

sleep 0.1

# Event 2: vCPUs = 2.0 (conflicts with Event 1)
echo "Creating event 2: vCPUs = 2.0 (conflicts with Event 1)"
psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -c "
INSERT INTO events (event_id, timestamp, event_type, event_source, instance_id, org_id, data) VALUES (
  gen_random_uuid(),
  '$TIMESTAMP'::timestamp with time zone,
  'snapshot',
  'rhelemeter',
  '$INSTANCE_ID',
  '$ORG_ID',
  '{
    \"event_id\": \"synthetic-event-2\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"record_date\": \"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",
    \"measurements\": [
      {
        \"value\": 2.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }'
);"

sleep 0.1

# Event 3: vCPUs = 3.0 (conflicts with Events 1 and 2)
echo "Creating event 3: vCPUs = 3.0 (conflicts with Events 1 and 2)"
psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -c "
INSERT INTO events (event_id, timestamp, event_type, event_source, instance_id, org_id, data) VALUES (
  gen_random_uuid(),
  '$TIMESTAMP'::timestamp with time zone,
  'snapshot',
  'rhelemeter',
  '$INSTANCE_ID',
  '$ORG_ID',
  '{
    \"event_id\": \"synthetic-event-3\",
    \"org_id\": \"$ORG_ID\",
    \"timestamp\": \"$TIMESTAMP\",
    \"instance_id\": \"$INSTANCE_ID\",
    \"event_type\": \"snapshot\",
    \"event_source\": \"rhelemeter\",
    \"record_date\": \"$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)\",
    \"measurements\": [
      {
        \"value\": 3.0,
        \"metric_id\": \"vCPUs\"
      }
    ]
  }'
);"

echo
echo "Synthetic events created. Now triggering conflict resolution..."
echo

# Trigger conflict resolution by calling the metering API
echo "Triggering metering API to process conflicts..."
curl -s -X 'POST' "http://localhost:8005/api/swatch-metrics/v1/internal/metering/rhel-for-x86-els-payg?orgId=$ORG_ID&endDate=$TIMESTAMP&rangeInMinutes=60" \
  -H 'accept: */*' \
  -H 'x-rh-swatch-psk: placeholder' \
  -w "Status: %{http_code}\n" -o /dev/null

echo
echo "Waiting for processing..."
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

# Count total events
TOTAL_EVENTS=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT COUNT(*) 
FROM events 
WHERE data->>'org_id' = '$ORG_ID'
  AND data->>'instance_id' = '$INSTANCE_ID';" | tr -d ' ')

echo "Total events found: $TOTAL_EVENTS"

# Count events with same timestamp (conflicts)
SAME_TIMESTAMP_EVENTS=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT COUNT(*) 
FROM (
  SELECT data->>'timestamp' as timestamp
  FROM events 
  WHERE data->>'org_id' = '$ORG_ID'
    AND data->>'instance_id' = '$INSTANCE_ID'
  GROUP BY data->>'timestamp'
  HAVING COUNT(*) > 1
) t;" | tr -d ' ')

echo "Events with same timestamp (conflicts): $SAME_TIMESTAMP_EVENTS"

# Check for deduction events (negative values)
DEDUCTION_EVENTS=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT COUNT(*) 
FROM events 
WHERE data->>'org_id' = '$ORG_ID'
  AND data->>'instance_id' = '$INSTANCE_ID'
  AND (data->'measurements'->0->>'value')::numeric < 0;" | tr -d ' ')

echo "Deduction events (negative values): $DEDUCTION_EVENTS"

# Analysis
if [ "$TOTAL_EVENTS" -gt 3 ] && [ "$DEDUCTION_EVENTS" -gt 0 ]; then
    echo "❌ BUG DETECTED: Multiple events with deductions found"
    echo "   This indicates the cascading deductions bug is present"
    echo "   Expected: 1 event, Found: $TOTAL_EVENTS events with $DEDUCTION_EVENTS deductions"
elif [ "$TOTAL_EVENTS" -eq 1 ]; then
    echo "✅ GOOD: Only 1 event found - conflict resolution working correctly"
    echo "   This suggests your fix is working!"
elif [ "$TOTAL_EVENTS" -eq 3 ]; then
    echo "⚠️  All 3 original events still present - no conflict resolution occurred"
    echo "   This might indicate the conflict resolution isn't being triggered"
else
    echo "⚠️  Unexpected result: $TOTAL_EVENTS events found"
fi

echo
echo "=== Expected Behavior ==="
echo "With BUG (main branch): Multiple events with cascading deductions"
echo "With FIX (your branch): Single event with proper conflict resolution"
echo
echo "Test complete!" 