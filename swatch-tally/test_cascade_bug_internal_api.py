#!/usr/bin/env python3
"""
Reproducer for SWATCH-3545 using internal API (matching original test conditions)

This script reproduces the exact test steps from SWATCH-3545 using the internal API
instead of Kafka, which may trigger different code paths and reproduce the bug.

1. Send event with cores: 1.0
2. Send event with cores: 10.0 (should create deduction of -1.0)
3. Send event with cores: 100.0 (should create deduction of -10.0, but bug creates -1.0)

Usage:
    python3 test_cascade_bug_internal_api.py

Prerequisites:
    - swatch-tally service running
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

def send_internal_api_event(instance_id, cores_value, timestamp):
    """Send an event via the internal API."""
    event = {
        "event_source": "prometheus",
        "event_type": "Cores",
        "org_id": ORG_ID,
        "instance_id": instance_id,
        "service_type": "Rhacs Cluster",
        "timestamp": timestamp,
        "expiration": BASE_EXPIRATION,
        "display_name": f"automation_rhacs_cluster_{instance_id}",
        "measurements": [{"value": cores_value, "metric_id": "Cores"}],
        "product_tag": ["rhacs"],
        "conversion": False,
        "isHypervisor": False,
        "isVirtual": False,
        "isUnmappedGuest": False,
        "role": "rhacs",
        "sla": "Premium"
    }
    
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
        print(f"Sent event with cores={cores_value}, status={response.status_code}")
        return True
    except Exception as e:
        print(f"Failed to send event with cores={cores_value}: {e}")
        return False

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

def main():
    """Main function to reproduce SWATCH-3545 bug using internal API."""
    print("=== SWATCH-3545 Bug Reproducer (Internal API) ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    # Clear existing test events
    print("\n1. Clearing existing test events...")
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Step 1: Send event with cores: 1.0
    print("\n2. Step 1: Sending event with cores=1.0...")
    if not send_internal_api_event(INSTANCE_ID, 1.0, BASE_TIMESTAMP):
        print("Failed to send first message")
        return
    
    time.sleep(2)  # Wait for processing
    
    # Step 2: Send event with cores: 10.0
    print("\n3. Step 2: Sending event with cores=10.0...")
    if not send_internal_api_event(INSTANCE_ID, 10.0, BASE_TIMESTAMP):
        print("Failed to send second message")
        return
    
    time.sleep(2)  # Wait for processing
    
    # Step 3: Send event with cores: 100.0
    print("\n4. Step 3: Sending event with cores=100.0...")
    if not send_internal_api_event(INSTANCE_ID, 100.0, BASE_TIMESTAMP):
        print("Failed to send third message")
        return
    
    # Wait for processing
    print("\n5. Waiting for event processing...")
    time.sleep(5)  # Give time for processing
    
    # Read and display results
    print("\n6. Reading events from database...")
    events = get_events(ORG_ID, INSTANCE_ID)
    print_events(events)
    
    # Analyze for the SWATCH-3545 bug
    bug_detected = analyze_swatch_3545_bug(events)
    
    if bug_detected:
        print("\n🎯 SUCCESS: SWATCH-3545 bug reproduced!")
        print("The conflict resolution is creating incorrect amendment events.")
    else:
        print("\n✅ No SWATCH-3545 bug detected in this run.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc() 