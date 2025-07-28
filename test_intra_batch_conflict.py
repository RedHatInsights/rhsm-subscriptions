#!/usr/bin/env python3
"""
Test for intra-batch conflict resolution bug.

This script sends multiple events with the same instance_id and timestamp
in a single batch to trigger the REQUIRES_NEW transaction isolation bug.
"""

import requests
import json
import time
from datetime import datetime, timezone
import uuid

# Configuration
TALLY_BASE_URL = "http://localhost:8003"
EVENTS_ENDPOINT = f"{TALLY_BASE_URL}/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
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

def send_events(events):
    """Send events to the tally service."""
    headers = {
        "Content-Type": "application/json",
        "x-rh-swatch-psk": PSK_HEADER
    }

    response = requests.post(EVENTS_ENDPOINT, headers=headers, json=events)
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
    """Print events in a readable format."""
    print("\nEvents in database:")
    print("-" * 80)
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
        print(f"{i+1}. Event ID: {event_id}")
        print(f"   Timestamp: {timestamp}")
        print(f"   Instance ID: {instance_id}")
        print(f"   Measurements: {measurements}")
        print(f"   Value: {value}")
        print(f"   Record Date: {record_date}")
        print()

def main():
    """Main test function to reproduce the intra-batch conflict bug."""
    print("=== Intra-Batch Conflict Resolution Bug Test ===")
    print()

    # Test parameters
    org_id = "13259775"
    instance_id = f"test-batch-conflict-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"
    metric_id = "Cores"

    print(f"Test Parameters:")
    print(f"  Org ID: {org_id}")
    print(f"  Instance ID: {instance_id}")
    print(f"  Timestamp: {timestamp}")
    print(f"  Metric ID: {metric_id}")
    print()

    # Create multiple events with the same instance_id and timestamp
    # This should trigger intra-batch conflict resolution
    print("Sending batch of conflicting events...")

    events = [
        create_event(org_id, instance_id, timestamp, metric_id, 1.0),
        create_event(org_id, instance_id, timestamp, metric_id, 2.0),
        create_event(org_id, instance_id, timestamp, metric_id, 3.0)
    ]

    success = send_events(events)
    if not success:
        print("Failed to send events")
        return

    time.sleep(3)  # Wait for processing

    # Check what was created
    events_result = query_events(org_id, instance_id, timestamp)
    print_events(events_result)

    # Analyze the results
    print("=== Analysis ===")
    print("Expected behavior:")
    print("  - Should see deduction events for conflicts")
    print("  - Final value should be 3.0 (the last event)")
    print()

    # Extract values for analysis
    values = []
    for event in events_result:
        event_id, timestamp, instance_id, measurements, record_date = event
        # Handle measurements which might be a list or JSON string
        if isinstance(measurements, str):
            measurements_data = json.loads(measurements)
        elif isinstance(measurements, list):
            measurements_data = measurements
        else:
            measurements_data = []

        if measurements_data:
            value = measurements_data[0]['value']
            values.append(value)

    print(f"Actual values: {values}")

    # Check for the bug pattern
    if len(values) >= 3:
        # Look for the pattern we saw earlier: [1.0, -1.0, 2.0, -2.0, 3.0]
        if values == [1.0, -1.0, 2.0, -2.0, 3.0]:
            print("🎯 INTRA-BATCH CONFLICT BUG REPRODUCED!")
            print("   This shows the REQUIRES_NEW transaction isolation issue")
        elif values == [1.0, -1.0, 3.0]:
            print("✅ Correct behavior - only one deduction for the final value")
        else:
            print(f"❓ Unexpected pattern: {values}")
    else:
        print("❓ Not enough events to analyze")

if __name__ == "__main__":
    main()