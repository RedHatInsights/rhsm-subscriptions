#!/usr/bin/env python3
"""
Aggressive reproducer for SWATCH-3545 using multiple approaches

This script tries various aggressive techniques to reproduce the SWATCH-3545 bug:
1. Rapid-fire requests with minimal delays
2. Multiple concurrent requests
3. Different timing patterns
4. Attempts to replicate exact IQE conditions

Usage:
    python3 test_cascade_bug_aggressive.py

Prerequisites:
    - swatch-tally service running on localhost:8002
    - PostgreSQL running with rhsm-subscriptions database
"""

import requests
import psycopg2
import json
import time
import uuid
import threading
import concurrent.futures
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

def test_rapid_fire():
    """Test 1: Rapid-fire requests with minimal delays."""
    print("\n=== Test 1: Rapid-Fire Requests ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send all three events as fast as possible
    print("Sending events in rapid succession...")
    
    # Event 1
    send_internal_api_event(INSTANCE_ID, 1.0, BASE_TIMESTAMP)
    time.sleep(0.001)  # Minimal delay
    
    # Event 2
    send_internal_api_event(INSTANCE_ID, 10.0, BASE_TIMESTAMP)
    time.sleep(0.001)  # Minimal delay
    
    # Event 3
    send_internal_api_event(INSTANCE_ID, 100.0, BASE_TIMESTAMP)
    
    time.sleep(10)  # Wait for processing
    
    events = get_events(ORG_ID, INSTANCE_ID)
    return analyze_swatch_3545_bug(events)

def test_concurrent_requests():
    """Test 2: Concurrent requests using ThreadPoolExecutor."""
    print("\n=== Test 2: Concurrent Requests ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    print("Sending events concurrently...")
    
    def send_event(cores_value):
        return send_internal_api_event(INSTANCE_ID, cores_value, BASE_TIMESTAMP)
    
    # Send all three events concurrently
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        futures = [
            executor.submit(send_event, 1.0),
            executor.submit(send_event, 10.0),
            executor.submit(send_event, 100.0)
        ]
        
        # Wait for all to complete
        concurrent.futures.wait(futures)
    
    time.sleep(10)  # Wait for processing
    
    events = get_events(ORG_ID, INSTANCE_ID)
    return analyze_swatch_3545_bug(events)

def test_iqe_timing():
    """Test 3: Replicate IQE timing pattern."""
    print("\n=== Test 3: IQE Timing Pattern ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    print("Replicating IQE timing pattern...")
    
    # Based on IQE log timing
    send_internal_api_event(INSTANCE_ID, 1.0, BASE_TIMESTAMP)
    time.sleep(0.1)  # Small delay like in IQE
    
    send_internal_api_event(INSTANCE_ID, 10.0, BASE_TIMESTAMP)
    time.sleep(0.1)  # Small delay like in IQE
    
    send_internal_api_event(INSTANCE_ID, 100.0, BASE_TIMESTAMP)
    
    time.sleep(10)  # Wait for processing
    
    events = get_events(ORG_ID, INSTANCE_ID)
    return analyze_swatch_3545_bug(events)

def test_batch_processing():
    """Test 4: Simulate batch processing by sending multiple events quickly."""
    print("\n=== Test 4: Batch Processing Simulation ===")
    
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    print("Simulating batch processing...")
    
    # Send multiple events in quick succession to simulate batch processing
    events_to_send = [
        (1.0, BASE_TIMESTAMP),
        (10.0, BASE_TIMESTAMP),
        (100.0, BASE_TIMESTAMP)
    ]
    
    for cores_value, timestamp in events_to_send:
        send_internal_api_event(INSTANCE_ID, cores_value, timestamp)
        time.sleep(0.01)  # Very small delay
    
    time.sleep(10)  # Wait for processing
    
    events = get_events(ORG_ID, INSTANCE_ID)
    return analyze_swatch_3545_bug(events)

def test_multiple_runs():
    """Test 5: Run multiple iterations to catch intermittent bugs."""
    print("\n=== Test 5: Multiple Iterations ===")
    
    bug_detected = False
    
    for run in range(5):
        print(f"\n--- Run {run + 1}/5 ---")
        
        clear_test_events(ORG_ID, INSTANCE_ID)
        
        # Send events with different timing each run
        send_internal_api_event(INSTANCE_ID, 1.0, BASE_TIMESTAMP)
        time.sleep(0.01 * (run + 1))  # Varying delays
        
        send_internal_api_event(INSTANCE_ID, 10.0, BASE_TIMESTAMP)
        time.sleep(0.01 * (run + 1))  # Varying delays
        
        send_internal_api_event(INSTANCE_ID, 100.0, BASE_TIMESTAMP)
        
        time.sleep(8)  # Wait for processing
        
        events = get_events(ORG_ID, INSTANCE_ID)
        if analyze_swatch_3545_bug(events):
            bug_detected = True
            print(f"🚨 BUG DETECTED IN RUN {run + 1}! 🚨")
            break
    
    return bug_detected

def main():
    """Main function to run all aggressive tests."""
    print("=== SWATCH-3545 Aggressive Bug Reproducer ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    bug_detected = False
    
    # Run all test scenarios
    tests = [
        ("Rapid Fire", test_rapid_fire),
        ("Concurrent Requests", test_concurrent_requests),
        ("IQE Timing", test_iqe_timing),
        ("Batch Processing", test_batch_processing),
        ("Multiple Runs", test_multiple_runs)
    ]
    
    for test_name, test_func in tests:
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
        print(f"\n✅ No SWATCH-3545 bug detected in any aggressive test.")
        print("The bug may require very specific conditions or environment setup.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc() 