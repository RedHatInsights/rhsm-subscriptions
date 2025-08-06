#!/bin/bash

set -e

echo "🧪 Testing SWATCH-3545 Cascading Deduction Bug with Network Configuration"

# Test configuration
BASE_URL="http://localhost:8002"
HEADERS="-H 'x-rh-swatch-psk: placeholder' -H 'Origin: console.redhat.com' -H 'Content-Type: application/json'"

echo "📋 Sending baseline event..."
BASELINE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  $HEADERS \
  -d '[
    {
      "orgId": "test-org-123",
      "timestamp": "2025-08-05T23:15:00Z",
      "instanceId": "test-instance-1",
      "eventType": "snapshot",
      "eventSource": "test",
      "displayName": "Test Event",
      "measurements": [
        {
          "metricId": "test-metric",
          "value": 0.2857142857142857,
          "uom": "cores"
        }
      ]
    }
  ]')

echo "✅ Baseline event sent: $BASELINE_RESPONSE"

echo "📋 Sending second event with same EventKey but different value..."
SECOND_RESPONSE=$(curl -s -X POST "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  $HEADERS \
  -d '[
    {
      "orgId": "test-org-123",
      "timestamp": "2025-08-05T23:15:00Z",
      "instanceId": "test-instance-1",
      "eventType": "snapshot",
      "eventSource": "test",
      "displayName": "Test Event 2",
      "measurements": [
        {
          "metricId": "test-metric",
          "value": 1.0,
          "uom": "cores"
        }
      ]
    }
  ]')

echo "✅ Second event sent: $SECOND_RESPONSE"

echo "📋 Sending third event with same EventKey but different value..."
THIRD_RESPONSE=$(curl -s -X POST "$BASE_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
  $HEADERS \
  -d '[
    {
      "orgId": "test-org-123",
      "timestamp": "2025-08-05T23:15:00Z",
      "instanceId": "test-instance-1",
      "eventType": "snapshot",
      "eventSource": "test",
      "displayName": "Test Event 3",
      "measurements": [
        {
          "metricId": "test-metric",
          "value": 1.0,
          "uom": "cores"
        }
      ]
    }
  ]')

echo "✅ Third event sent: $THIRD_RESPONSE"

echo "📊 Checking database for events and deductions..."
DB_RESULTS=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT 
  COUNT(*) as total_events,
  COUNT(CASE WHEN amendment_type = 'deduction' THEN 1 END) as deduction_count,
  COUNT(CASE WHEN amendment_type IS NULL THEN 1 END) as regular_events,
  STRING_AGG(DISTINCT measurement_value::text, ', ' ORDER BY measurement_value::text) as unique_values
FROM event_record 
WHERE org_id = 'test-org-123' 
  AND instance_id = 'test-instance-1'
  AND timestamp = '2025-08-05 23:15:00'::timestamp;
")

echo "📊 Database Results:"
echo "$DB_RESULTS"

echo "🔍 Checking for duplicate deductions (BUG indicator)..."
DUPLICATE_DEDUCTIONS=$(psql -h localhost -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
SELECT 
  measurement_value,
  COUNT(*) as count
FROM event_record 
WHERE org_id = 'test-org-123' 
  AND instance_id = 'test-instance-1'
  AND timestamp = '2025-08-05 23:15:00'::timestamp
  AND amendment_type = 'deduction'
GROUP BY measurement_value
HAVING COUNT(*) > 1;
")

if [ -n "$DUPLICATE_DEDUCTIONS" ]; then
  echo "🚨 BUG FOUND! Duplicate deductions detected:"
  echo "$DUPLICATE_DEDUCTIONS"
else
  echo "✅ No duplicate deductions found"
fi

echo "�� Test completed!" 