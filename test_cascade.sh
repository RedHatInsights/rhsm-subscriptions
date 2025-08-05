#!/bin/bash

# Test script for reproducing the cascading deductions bug

echo "=== Cascading Deductions Bug Test ==="
echo

# Configuration
ORG_ID="13259775"
INSTANCE_ID="i-test-cascade-bug-12345"

echo "Configuration:"
echo "  Org ID: $ORG_ID"
echo "  Instance ID: $INSTANCE_ID"
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

# Run the Python test
echo "Running cascade test..."
if command -v python3 &> /dev/null; then
    python3 test_cascade_bug.py
else
    echo "ERROR: python3 not found. Please install Python 3 and required packages:"
    echo "  pip install requests"
    exit 1
fi

echo
echo "Waiting for Kafka consumer to process events..."
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