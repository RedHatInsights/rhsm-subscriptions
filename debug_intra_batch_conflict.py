#!/usr/bin/env python3
"""
Debug script for manually reproducing the intra-batch conflict bug.

This script allows you to:
1. Send events with precise timing control
2. Pause between events to create race conditions
3. Monitor the database state in real-time
4. Trigger the bug on demand
"""

import requests
import json
import time
import threading
from datetime import datetime, timezone
import uuid
import psycopg2

# Configuration
TALLY_BASE_URL = "http://localhost:8003"
EVENTS_ENDPOINT = f"{TALLY_BASE_URL}/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
PSK_HEADER = "placeholder"
ORG_ID = "13259775"

def create_event(org_id, instance_id, timestamp, metric_id, value, event_source="rhelemeter"):
    """Create a single event."""
    return {
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

def send_events(events):
    """Send events to the tally service."""
    headers = {
        "Content-Type": "application/json",
        "x-rh-swatch-psk": PSK_HEADER
    }

    response = requests.post(EVENTS_ENDPOINT, json=events, headers=headers)
    return response.status_code == 200, response.text

def query_events(org_id, instance_id, timestamp):
    """Query events from the database."""
    try:
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
    except Exception as e:
        print(f"Database query failed: {e}")
        return []

def print_events(events):
    """Print events in a readable format."""
    print(f"\nEvents in database ({len(events)} found):")
    print("-" * 80)

    values = []
    for i, event in enumerate(events):
        if len(event) >= 5:
            event_id, timestamp, instance_id, measurements, record_date = event
            print(f"{i+1}. Event ID: {event_id}")
            print(f"   Timestamp: {timestamp}")
            print(f"   Instance ID: {instance_id}")
            print(f"   Measurements: {measurements}")
            print(f"   Record Date: {record_date}")

            # Extract value
            try:
                if isinstance(measurements, str):
                    measurements_data = json.loads(measurements)
                elif isinstance(measurements, list):
                    measurements_data = measurements
                else:
                    measurements_data = []

                if measurements_data and len(measurements_data) > 0:
                    value = measurements_data[0].get('value')
                    if value is not None:
                        values.append(value)
            except Exception as e:
                print(f"   Warning: Could not parse measurements: {e}")
            print()
        else:
            print(f"{i+1}. Malformed event: {event}")

    print(f"Values: {values}")
    return values

def debug_scenario_1_sequential_events():
    """Scenario 1: Send events one by one with pauses."""
    print("\n=== Debug Scenario 1: Sequential Events ===")
    print("This should show correct behavior (no bug)")

    instance_id = f"debug-seq-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"

    print(f"Instance ID: {instance_id}")
    print(f"Timestamp: {timestamp}")

    # Send events one by one with pauses
    for i, value in enumerate([1.0, 2.0, 3.0], 1):
        print(f"\n--- Sending Event {i}: value = {value} ---")

        event = create_event(ORG_ID, instance_id, timestamp, "Cores", value)
        success, response = send_events([event])

        if success:
            print(f"✅ Event {i} sent successfully")
        else:
            print(f"❌ Event {i} failed: {response}")

        # Pause to let processing complete
        print("⏸️  Pausing for 2 seconds...")
        time.sleep(2)

        # Check current state
        events = query_events(ORG_ID, instance_id, timestamp)
        values = print_events(events)

        print(f"Current values: {values}")
        print("=" * 50)

    # Final check
    print("\n--- Final State ---")
    events = query_events(ORG_ID, instance_id, timestamp)
    final_values = print_events(events)

    if final_values == [1.0, -1.0, 3.0]:
        print("✅ Correct behavior - no bug")
    else:
        print(f"❓ Unexpected pattern: {final_values}")

def debug_scenario_2_batch_events():
    """Scenario 2: Send events in a batch."""
    print("\n=== Debug Scenario 2: Batch Events ===")
    print("This should reproduce the bug")

    instance_id = f"debug-batch-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"

    print(f"Instance ID: {instance_id}")
    print(f"Timestamp: {timestamp}")

    # Create all events
    events = [
        create_event(ORG_ID, instance_id, timestamp, "Cores", 1.0),
        create_event(ORG_ID, instance_id, timestamp, "Cores", 2.0),
        create_event(ORG_ID, instance_id, timestamp, "Cores", 3.0)
    ]

    print(f"\n--- Sending Batch of {len(events)} Events ---")
    print("⏸️  PAUSE HERE: Set breakpoint in EventController.persistServiceInstances()")
    print("   - Break at the start of the method")
    print("   - Step through the REQUIRES_NEW transaction")
    print("   - Watch how conflict resolution misses uncommitted events")

    input("Press Enter to send the batch...")

    success, response = send_events(events)

    if success:
        print("✅ Batch sent successfully")
    else:
        print(f"❌ Batch failed: {response}")

    # Wait for processing
    print("⏸️  Waiting for processing...")
    time.sleep(3)

    # Check final state
    print("\n--- Final State ---")
    events_result = query_events(ORG_ID, instance_id, timestamp)
    final_values = print_events(events_result)

    if final_values == [1.0, -1.0, 2.0, -2.0, 3.0]:
        print("🎯 BUG REPRODUCED! Intra-batch conflict cascade detected!")
    elif final_values == [1.0, -1.0, 3.0]:
        print("✅ Correct behavior - bug might be fixed")
    else:
        print(f"❓ Unexpected pattern: {final_values}")

def debug_scenario_3_race_condition():
    """Scenario 3: Create a race condition with multiple threads."""
    print("\n=== Debug Scenario 3: Race Condition ===")
    print("This creates a race condition between multiple requests")

    instance_id = f"debug-race-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"

    print(f"Instance ID: {instance_id}")
    print(f"Timestamp: {timestamp}")

    def send_single_event(value, delay=0):
        """Send a single event with optional delay."""
        time.sleep(delay)
        event = create_event(ORG_ID, instance_id, timestamp, "Cores", value)
        success, response = send_events([event])
        print(f"Thread {value}: Event sent, success={success}")
        return success

    print("\n--- Creating Race Condition ---")
    print("⏸️  PAUSE HERE: Set breakpoints in EventController.persistServiceInstances()")
    print("   - Set breakpoint at the start of the method")
    print("   - Set breakpoint inside the REQUIRES_NEW transaction")
    print("   - Watch how multiple threads create race conditions")

    input("Press Enter to start race condition...")

    # Create threads to send events simultaneously
    threads = []
    for i, value in enumerate([1.0, 2.0, 3.0]):
        # Small delay to create overlap
        thread = threading.Thread(target=send_single_event, args=(value, i * 0.1))
        threads.append(thread)
        thread.start()

    # Wait for all threads to complete
    for thread in threads:
        thread.join()

    # Wait for processing
    print("⏸️  Waiting for processing...")
    time.sleep(3)

    # Check final state
    print("\n--- Final State ---")
    events_result = query_events(ORG_ID, instance_id, timestamp)
    final_values = print_events(events_result)

    print(f"Race condition result: {final_values}")

def debug_scenario_4_step_by_step():
    """Scenario 4: Step-by-step debugging with manual control."""
    print("\n=== Debug Scenario 4: Step-by-Step Debugging ===")
    print("Manual control over each step")

    instance_id = f"debug-step-{uuid.uuid4().hex[:8]}"
    timestamp = "2025-07-16T00:00:00Z"

    print(f"Instance ID: {instance_id}")
    print(f"Timestamp: {timestamp}")

    print("\n--- Step 1: Send first event ---")
    print("⏸️  Set breakpoint in EventController.persistServiceInstances()")
    print("   - Break at the start")
    print("   - Step through the method")
    print("   - Watch the transaction behavior")

    input("Press Enter to send first event...")

    event1 = create_event(ORG_ID, instance_id, timestamp, "Cores", 1.0)
    success, response = send_events([event1])
    print(f"Event 1 sent: success={success}")

    # Check state
    events = query_events(ORG_ID, instance_id, timestamp)
    values = print_events(events)

    print("\n--- Step 2: Send second event ---")
    print("⏸️  Now set breakpoint inside the REQUIRES_NEW transaction")
    print("   - Watch how conflict resolution works")
    print("   - See if it can see the first event")

    input("Press Enter to send second event...")

    event2 = create_event(ORG_ID, instance_id, timestamp, "Cores", 2.0)
    success, response = send_events([event2])
    print(f"Event 2 sent: success={success}")

    # Check state
    events = query_events(ORG_ID, instance_id, timestamp)
    values = print_events(events)

    print("\n--- Step 3: Send third event ---")
    print("⏸️  Continue debugging the transaction flow")

    input("Press Enter to send third event...")

    event3 = create_event(ORG_ID, instance_id, timestamp, "Cores", 3.0)
    success, response = send_events([event3])
    print(f"Event 3 sent: success={success}")

    # Final check
    print("\n--- Final State ---")
    events = query_events(ORG_ID, instance_id, timestamp)
    final_values = print_events(events)

    print(f"Step-by-step result: {final_values}")

def main():
    """Main debug menu."""
    print("🔍 Intra-Batch Conflict Bug Debugger")
    print("=" * 50)

    while True:
        print("\nChoose a debug scenario:")
        print("1. Sequential Events (should work correctly)")
        print("2. Batch Events (should reproduce bug)")
        print("3. Race Condition (multiple threads)")
        print("4. Step-by-Step Debugging")
        print("5. Exit")

        choice = input("\nEnter your choice (1-5): ").strip()

        if choice == "1":
            debug_scenario_1_sequential_events()
        elif choice == "2":
            debug_scenario_2_batch_events()
        elif choice == "3":
            debug_scenario_3_race_condition()
        elif choice == "4":
            debug_scenario_4_step_by_step()
        elif choice == "5":
            print("Exiting debugger...")
            break
        else:
            print("Invalid choice. Please enter 1-5.")

if __name__ == "__main__":
    main()