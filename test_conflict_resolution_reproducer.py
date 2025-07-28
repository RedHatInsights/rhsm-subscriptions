#!/usr/bin/env python3
"""
Reproducer for the conflict resolution bug in swatch-tally.

This script reproduces the bug where conflict resolution creates incorrect
deduction amounts due to REQUIRES_NEW transaction isolation.

Bug Description:
- Send event with cores: 1.0
- Send event with cores: 10.0 (creates -1.0 + 10.0)
- Send event with cores: 100.0 (should create -10.0 + 100.0, but creates -1.0 + 100.0)

The issue is that the conflict resolver runs in a REQUIRES_NEW transaction
and cannot see uncommitted events from the same batch.
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
    """Main test function to reproduce the bug."""
    print("=== Conflict Resolution Bug Reproducer ===")
    print()

    # Test parameters
    org_id = "13259775"
    instance_id = f"test-conflict-bug-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"
    metric_id = "Cores"

    print(f"Test Parameters:")
    print(f"  Org ID: {org_id}")
    print(f"  Instance ID: {instance_id}")
    print(f"  Timestamp: {timestamp}")
    print(f"  Metric ID: {metric_id}")
    print()

    # Step 1: Send initial event with cores: 1.0
    print("Step 1: Sending initial event with cores: 1.0")
    event1 = create_event(org_id, instance_id, timestamp, metric_id, 1.0)
    success = send_events([event1])
    if not success:
        print("Failed to send initial event")
        return

    time.sleep(2)  # Wait for processing

    # Check what was created
    events = query_events(org_id, instance_id, timestamp)
    print_events(events)

    # Step 2: Send event with cores: 10.0
    print("Step 2: Sending event with cores: 10.0")
    event2 = create_event(org_id, instance_id, timestamp, metric_id, 10.0)
    success = send_events([event2])
    if not success:
        print("Failed to send second event")
        return

    time.sleep(2)  # Wait for processing

    # Check what was created
    events = query_events(org_id, instance_id, timestamp)
    print_events(events)

    # Step 3: Send event with cores: 100.0
    print("Step 3: Sending event with cores: 100.0")
    event3 = create_event(org_id, instance_id, timestamp, metric_id, 100.0)
    success = send_events([event3])
    if not success:
        print("Failed to send third event")
        return

    time.sleep(2)  # Wait for processing

    # Check what was created
    events = query_events(org_id, instance_id, timestamp)
    print_events(events)

    # Analyze the results
    print("=== Analysis ===")
    print("Expected pattern:")
    print("  1. cores: 1.0 (initial)")
    print("  2. cores: -1.0 (deduction)")
    print("  3. cores: 10.0 (new value)")
    print("  4. cores: -10.0 (deduction for 10.0)")
    print("  5. cores: 100.0 (new value)")
    print()
    print("Bug: If step 4 shows cores: -1.0 instead of cores: -10.0, the bug is reproduced!")
    print()

    # Extract values for analysis
    values = []
    for event in events:
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

    # Check for the bug
    if len(values) >= 4:
        deduction_value = values[3]  # The deduction after the 10.0 event
        if deduction_value == -1.0:
            print("🎯 BUG REPRODUCED! The deduction should be -10.0 but is -1.0")
        elif deduction_value == -10.0:
            print("✅ No bug detected - deduction is correct (-10.0)")
        else:
            print(f"❓ Unexpected deduction value: {deduction_value}")
    else:
        print("❓ Not enough events to analyze")

if __name__ == "__main__":
    main()