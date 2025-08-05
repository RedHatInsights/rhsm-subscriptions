#!/usr/bin/env python3
"""
Intra-batch conflict resolution reproducer for SWATCH-3545

This script specifically targets the intra-batch conflict resolution scenario
that likely causes the SWATCH-3545 bug. The key insight is that when multiple
events with the same EventKey (org_id, timestamp, instance_id) are processed
in the same batch, the UsageConflictTracker is updated as each event is processed,
which can lead to incorrect deduction calculations.

Usage:
    python3 test_cascade_bug_intra_batch.py

Prerequisites:
    - swatch-tally service running on localhost:8002
    - PostgreSQL running with rhsm-subscriptions database
"""

import requests
import psycopg2
import json
import time
import uuid
from datetime import datetime

# Configuration
INTERNAL_API_URL = "http://localhost:8002"
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'rhsm-subscriptions',
    'user': 'postgres',
    'password': 'postgres'
}

# Test data - using the exact values from SWATCH-3545
ORG_ID = "13259775"
INSTANCE_ID = "i-0d83c7a09e2589d87"
BASE_TIMESTAMP = "2025-07-03T19:00:00Z"
BASE_EXPIRATION = "2025-07-03T20:00:00Z"

def get_db_connection():
    """Get a database connection."""
    return psycopg2.connect(**DB_CONFIG)

def clear_test_events(org_id, instance_id):
    """Clear existing events for the test instance."""
    with get_db_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                DELETE FROM events 
                WHERE data->>'org_id' = %s AND data->>'instance_id' = %s
            """, (org_id, instance_id))
            deleted_count = cur.rowcount
            print(f"Cleared {deleted_count} existing events for instance {instance_id}")
            conn.commit()

def send_batch_events(events):
    """Send multiple events in a single batch to trigger intra-batch conflict resolution."""
    try:
        response = requests.post(
            f"{INTERNAL_API_URL}/api/rhsm-subscriptions/v1/internal/rpc/tally/events",
            json=events,
            headers={
                "Content-Type": "application/json",
                "x-rh-swatch-psk": "placeholder",
                "Origin": "console.redhat.com"
            },
            timeout=30
        )
        response.raise_for_status()
        print(f"Sent batch of {len(events)} events, status={response.status_code}")
        return True
    except Exception as e:
        print(f"Failed to send batch: {e}")
        return False

def create_event(cores_value, event_id=None):
    """Create an event with the specified cores value."""
    if event_id is None:
        event_id = str(uuid.uuid4())
    
    return {
        "event_id": event_id,
        "event_source": "prometheus",
        "event_type": "Cores",
        "org_id": ORG_ID,
        "instance_id": INSTANCE_ID,
        "service_type": "Rhacs Cluster",
        "timestamp": BASE_TIMESTAMP,
        "expiration": BASE_EXPIRATION,
        "display_name": f"automation_rhacs_cluster_{INSTANCE_ID}",
        "measurements": [{"value": cores_value, "metric_id": "Cores"}],
        "product_tag": ["rhacs"],
        "conversion": False,
        "isHypervisor": False,
        "isVirtual": False,
        "isUnmappedGuest": False,
        "role": "rhacs",
        "sla": "Premium"
    }

def get_events(org_id, instance_id):
    """Get all events for the specified instance."""
    with get_db_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT 
                    data->>'event_id' as event_id,
                    data->>'timestamp' as timestamp,
                    data->>'instance_id' as instance_id,
                    data->'measurements' as measurements,
                    data->>'record_date' as record_date,
                    data->>'amendment_type' as amendment_type
                FROM events 
                WHERE data->>'org_id' = %s AND data->>'instance_id' = %s
                ORDER BY data->>'record_date'
            """, (org_id, instance_id))
            return cur.fetchall()

def print_events(events):
    """Print all events in a readable format."""
    if not events:
        print("No events found")
        return
    
    print(f"\n=== Found {len(events)} events ===")
    
    for i, event in enumerate(events, 1):
        event_id = event[0]
        timestamp = event[1]
        instance_id = event[2]
        measurements = event[3] if event[3] else []
        record_date = event[4]
        amendment_type = event[5]
        
        cores_value = measurements[0]['value'] if measurements else 'N/A'
        
        print(f"\nEvent {i}:")
        print(f"  Event ID: {event_id}")
        print(f"  Timestamp: {timestamp}")
        print(f"  Instance ID: {instance_id}")
        print(f"  Cores Value: {cores_value}")
        print(f"  Record Date: {record_date}")
        if amendment_type:
            print(f"  Amendment Type: {amendment_type}")

def analyze_swatch_3545_bug(events):
    """Analyze events to detect the SWATCH-3545 bug."""
    if not events:
        print("No events found")
        return False
    
    print(f"\n=== SWATCH-3545 Bug Analysis ===")
    
    # Find deduction events
    deduction_events = []
    for event in events:
        amendment_type = event[5]
        measurements = event[3] if event[3] else []
        if amendment_type == "deduction" and measurements:
            cores_value = measurements[0]['value']
            deduction_events.append((cores_value, event[4]))  # value, record_date
    
    if len(deduction_events) >= 2:
        print(f"Found {len(deduction_events)} deduction events:")
        for i, (value, record_date) in enumerate(deduction_events):
            print(f"  Deduction {i+1}: cores = {value} (record_date: {record_date})")
        
        # Check for the specific bug: second deduction should be -10.0 but is -1.0
        if len(deduction_events) >= 2:
            second_deduction = deduction_events[1][0]
            if second_deduction == -1.0:
                print(f"\n🚨 SWATCH-3545 BUG DETECTED! 🚨")
                print(f"Second deduction is {second_deduction} (should be -10.0)")
                print("This indicates the conflict resolution is using the wrong previous value")
                return True
            elif second_deduction == -10.0:
                print(f"\n✅ CORRECT BEHAVIOR: Second deduction is {second_deduction}")
                print("Conflict resolution is working correctly")
                return False
            else:
                print(f"\n⚠️  UNEXPECTED: Second deduction is {second_deduction}")
                return True
    
    print("No deduction events found or insufficient events for analysis")
    return False

def test_intra_batch_scenario_1():
    """Test 1: Send all three events in a single batch."""
    print("\n=== Test 1: Single Batch with All Events ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Create all three events with the same EventKey
    events = [
        create_event(1.0),
        create_event(10.0),
        create_event(100.0)
    ]
    
    print("Sending all three events in a single batch...")
    if not send_batch_events(events):
        return False
    
    time.sleep(10)  # Wait for processing
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_intra_batch_scenario_2():
    """Test 2: Send events in batches that should trigger intra-batch conflicts."""
    print("\n=== Test 2: Multiple Batches with Same EventKey ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # First batch: send event 1
    print("Sending first event...")
    if not send_batch_events([create_event(1.0)]):
        return False
    
    time.sleep(2)
    
    # Second batch: send events 2 and 3 together (same EventKey)
    print("Sending events 2 and 3 in same batch...")
    events_batch_2 = [
        create_event(10.0),
        create_event(100.0)
    ]
    if not send_batch_events(events_batch_2):
        return False
    
    time.sleep(10)  # Wait for processing
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_intra_batch_scenario_3():
    """Test 3: Send events with different product tags in same batch."""
    print("\n=== Test 3: Same Batch with Different Product Tags ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Create events with different product tags but same EventKey
    event1 = create_event(1.0)
    event1["product_tag"] = ["rhacs"]
    
    event2 = create_event(10.0)
    event2["product_tag"] = ["rhacs"]
    
    event3 = create_event(100.0)
    event3["product_tag"] = ["rhacs"]
    
    events = [event1, event2, event3]
    
    print("Sending events with same product tag in single batch...")
    if not send_batch_events(events):
        return False
    
    time.sleep(10)  # Wait for processing
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_intra_batch_scenario_4():
    """Test 4: Rapid successive batches to trigger race conditions."""
    print("\n=== Test 4: Rapid Successive Batches ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send events in rapid succession
    print("Sending event 1...")
    if not send_batch_events([create_event(1.0)]):
        return False
    
    time.sleep(0.1)  # Very small delay
    
    print("Sending event 2...")
    if not send_batch_events([create_event(10.0)]):
        return False
    
    time.sleep(0.1)  # Very small delay
    
    print("Sending event 3...")
    if not send_batch_events([create_event(100.0)]):
        return False
    
    time.sleep(10)  # Wait for processing
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def main():
    """Main function to test intra-batch conflict resolution scenarios."""
    print("=== SWATCH-3545 Intra-Batch Conflict Resolution Reproducer ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    bug_detected = False
    
    # Test different intra-batch scenarios
    scenarios = [
        ("Single Batch All Events", test_intra_batch_scenario_1),
        ("Multiple Batches Same EventKey", test_intra_batch_scenario_2),
        ("Same Batch Different Product Tags", test_intra_batch_scenario_3),
        ("Rapid Successive Batches", test_intra_batch_scenario_4)
    ]
    
    for test_name, test_func in scenarios:
        print(f"\n{'='*60}")
        print(f"Running: {test_name}")
        print(f"{'='*60}")
        
        try:
            if test_func():
                bug_detected = True
                print(f"\n🎯 SUCCESS: SWATCH-3545 bug reproduced in {test_name}!")
                break
        except Exception as e:
            print(f"Error in {test_name}: {e}")
            continue
    
    if not bug_detected:
        print(f"\n✅ No SWATCH-3545 bug detected in any intra-batch scenario.")
        print("The intra-batch conflict resolution appears to be working correctly.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc() 