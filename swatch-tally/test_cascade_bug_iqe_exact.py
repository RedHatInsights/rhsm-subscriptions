#!/usr/bin/env python3
"""
Exact IQE conditions reproducer for SWATCH-3545

This script attempts to exactly replicate the conditions from the IQE test
that triggered the SWATCH-3545 bug. Based on the IQE log analysis and code
review, this focuses on the specific timing and processing patterns.

Usage:
    python3 test_cascade_bug_iqe_exact.py

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

def send_single_event(event):
    """Send a single event via the internal API."""
    try:
        response = requests.post(
            f"{INTERNAL_API_URL}/api/rhsm-subscriptions/v1/internal/rpc/tally/events",
            json=[event],
            headers={
                "Content-Type": "application/json",
                "x-rh-swatch-psk": "placeholder",
                "Origin": "console.redhat.com"
            },
            timeout=10
        )
        response.raise_for_status()
        print(f"Sent event with cores={event['measurements'][0]['value']}, status={response.status_code}")
        return True
    except Exception as e:
        print(f"Failed to send event: {e}")
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

def test_iqe_exact_timing():
    """Test 1: Replicate exact IQE timing pattern."""
    print("\n=== Test 1: Exact IQE Timing Pattern ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Based on IQE log analysis, send events with specific timing
    print("Step 1: Sending event with cores=1.0...")
    event1 = create_event(1.0)
    if not send_single_event(event1):
        return False
    
    # Wait for processing (like in IQE)
    time.sleep(2)
    
    print("Step 2: Sending event with cores=10.0...")
    event2 = create_event(10.0)
    if not send_single_event(event2):
        return False
    
    # Wait for processing (like in IQE)
    time.sleep(2)
    
    print("Step 3: Sending event with cores=100.0...")
    event3 = create_event(100.0)
    if not send_single_event(event3):
        return False
    
    # Wait for processing
    time.sleep(10)
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_iqe_batch_processing():
    """Test 2: Replicate IQE batch processing behavior."""
    print("\n=== Test 2: IQE Batch Processing ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send events in a way that might trigger batch processing issues
    print("Sending events in rapid succession to trigger batch processing...")
    
    # Send first event
    event1 = create_event(1.0)
    if not send_single_event(event1):
        return False
    
    # Very small delay to ensure they're processed together
    time.sleep(0.01)
    
    # Send second event
    event2 = create_event(10.0)
    if not send_single_event(event2):
        return False
    
    # Very small delay
    time.sleep(0.01)
    
    # Send third event
    event3 = create_event(100.0)
    if not send_single_event(event3):
        return False
    
    # Wait for processing
    time.sleep(10)
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_iqe_transaction_isolation():
    """Test 3: Test transaction isolation issues."""
    print("\n=== Test 3: Transaction Isolation Test ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send events in a way that might trigger transaction isolation issues
    print("Testing transaction isolation with rapid events...")
    
    # Send all events very quickly to potentially trigger transaction isolation issues
    events = [create_event(1.0), create_event(10.0), create_event(100.0)]
    
    for i, event in enumerate(events):
        print(f"Sending event {i+1}/3...")
        if not send_single_event(event):
            return False
        time.sleep(0.001)  # Minimal delay
    
    # Wait for processing
    time.sleep(10)
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def test_iqe_usage_conflict_tracker():
    """Test 4: Test UsageConflictTracker behavior."""
    print("\n=== Test 4: UsageConflictTracker Test ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Test the specific scenario where UsageConflictTracker might fail
    print("Testing UsageConflictTracker with specific timing...")
    
    # Send first event and wait for it to be fully processed
    event1 = create_event(1.0)
    if not send_single_event(event1):
        return False
    
    time.sleep(5)  # Wait for full processing
    
    # Send second and third events together to test intra-batch conflict resolution
    print("Sending events 2 and 3 together...")
    event2 = create_event(10.0)
    event3 = create_event(100.0)
    
    # Send them as separate requests but very quickly
    if not send_single_event(event2):
        return False
    time.sleep(0.001)  # Minimal delay
    if not send_single_event(event3):
        return False
    
    # Wait for processing
    time.sleep(10)
    
    events_result = get_events(ORG_ID, INSTANCE_ID)
    print_events(events_result)
    return analyze_swatch_3545_bug(events_result)

def main():
    """Main function to test exact IQE conditions."""
    print("=== SWATCH-3545 Exact IQE Conditions Reproducer ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    bug_detected = False
    
    # Test different IQE-specific scenarios
    scenarios = [
        ("Exact IQE Timing", test_iqe_exact_timing),
        ("IQE Batch Processing", test_iqe_batch_processing),
        ("Transaction Isolation", test_iqe_transaction_isolation),
        ("UsageConflictTracker", test_iqe_usage_conflict_tracker)
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
        print(f"\n✅ No SWATCH-3545 bug detected in any IQE-specific scenario.")
        print("The exact IQE conditions may require different environment setup or configuration.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc() 