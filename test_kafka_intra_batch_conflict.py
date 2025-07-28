#!/usr/bin/env python3
"""
Test to reproduce the intra-batch conflict bug through the Kafka consumer path.

This test sends events to Kafka, which will be consumed by the ServiceInstanceMessageConsumer,
which calls EventController.persistServiceInstances() - the method we fixed.
"""

import requests
import json
import time
import uuid
from datetime import datetime, timezone

# Configuration
KAFKA_BASE_URL = "http://localhost:8003"
KAFKA_ENDPOINT = f"{KAFKA_BASE_URL}/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
PSK_HEADER = "placeholder"

def create_event(org_id, instance_id, timestamp, metric_id, value, event_source="rhelemeter"):
    """Create a single event with the specified parameters."""
    event = {
        "org_id": org_id,
        "instance_id": instance_id,
        "timestamp": timestamp,
        "event_type": f"snapshot_rhel-for-x86-els-payg_{metric_id.lower()}",
        "event_source": event_source,
        "measurements": [{"metric_id": metric_id, "value": value}],
        "product_tag": ["rhel-for-x86-els-payg"],
        "service_type": "RHEL System",
        "display_name": f"test-instance-{instance_id}",
        "sla": "Premium",
        "usage": "Production",
        "billing_provider": "aws",
        "billing_account_id": "test-account-123"
    }
    return event

def send_events_to_kafka(events):
    """Send events to Kafka through the internal RPC endpoint."""
    headers = {
        "Content-Type": "application/json",
        "x-rh-swatch-psk": PSK_HEADER
    }

    # Convert events to JSON strings (as expected by the Kafka consumer)
    event_json_list = [json.dumps(event) for event in events]

    response = requests.post(KAFKA_ENDPOINT, headers=headers, json=event_json_list)
    print(f"Response status: {response.status_code}")
    if response.status_code != 200:
        print(f"Error: {response.text}")
    return response.status_code == 200

def query_events(org_id, instance_id, timestamp):
    """Query events from the database to see what was created."""
    import psycopg2

    conn = psycopg2.connect(
        host="localhost",
        database="rhsm-subscriptions",
        user="rhsm-subscriptions",
        password="rhsm-subscriptions"
    )

    cursor = conn.cursor()
    query = """
    SELECT
        data->>'event_id' as event_id,
        data->>'timestamp' as timestamp,
        data->>'instance_id' as instance_id,
        data->'measurements' as measurements,
        data->>'record_date' as record_date
    FROM events
    WHERE data->>'org_id' = %s
      AND data->>'instance_id' = %s
      AND data->>'timestamp' = %s
    ORDER BY data->>'record_date'
    """

    cursor.execute(query, (org_id, instance_id, timestamp))
    results = cursor.fetchall()

    cursor.close()
    conn.close()

    return results

def print_events(events):
    """Print events in a readable format and return the values."""
    print("\nEvents in database:")
    print("-" * 80)
    values = []
    for i, event in enumerate(events):
        event_id, timestamp, instance_id, measurements, record_date = event
        # Handle measurements which might be a list or JSON string
        if isinstance(measurements, str):
            measurements_data = json.loads(measurements)
        elif isinstance(measurements, list):
            measurements_data = measurements
        else:
            measurements_data = []

        value = measurements_data[0]['value'] if measurements_data else 'N/A'
        values.append(value)
        print(f"{i+1}. Event ID: {event_id}")
        print(f"   Timestamp: {timestamp}")
        print(f"   Instance ID: {instance_id}")
        print(f"   Measurements: {measurements}")
        print(f"   Value: {value}")
        print(f"   Record Date: {record_date}")
        print()

    return values

def main():
    """Main test function to reproduce the intra-batch conflict bug through Kafka."""
    print("=== Kafka Intra-Batch Conflict Resolution Bug Test ===")
    print()

    # Test parameters
    org_id = "13259775"
    instance_id = f"test-kafka-conflict-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"
    metric_id = "Cores"

    print("Test Parameters:")
    print(f"  Org ID: {org_id}")
    print(f"  Instance ID: {instance_id}")
    print(f"  Timestamp: {timestamp}")
    print(f"  Metric ID: {metric_id}")
    print()

    # Create conflicting events (same instance_id and timestamp, different values)
    events = [
        create_event(org_id, instance_id, timestamp, metric_id, 1.0),
        create_event(org_id, instance_id, timestamp, metric_id, 2.0),
        create_event(org_id, instance_id, timestamp, metric_id, 3.0)
    ]

    print("Sending batch of conflicting events to Kafka...")
    success = send_events_to_kafka(events)

    if not success:
        print("❌ Failed to send events to Kafka")
        return

    # Wait for Kafka processing
    print("⏳ Waiting for Kafka processing...")
    time.sleep(5)

    # Query the database to see what events were created
    events_result = query_events(org_id, instance_id, timestamp)

    if not events_result:
        print("❌ No events found in database")
        return

    values = print_events(events_result)

    print("=== Analysis ===")
    print("Expected behavior:")
    print("  - Should see deduction events for conflicts")
    print("  - Final value should be 3.0 (the last event)")
    print()
    print(f"Actual values: {values}")

    # Check if the bug is reproduced
    if values == [1.0, -1.0, 2.0, -2.0, 3.0]:
        print("🎯 KAFKA INTRA-BATCH CONFLICT BUG REPRODUCED!")
        print("   This shows the REQUIRES_NEW transaction isolation issue")
        print("   in the Kafka consumer path (EventController.persistServiceInstances)")
    elif values == [1.0, -1.0, 3.0]:
        print("✅ Correct behavior - bug is fixed in Kafka consumer path")
    else:
        print(f"❓ Unexpected pattern: {values}")

if __name__ == "__main__":
    main()