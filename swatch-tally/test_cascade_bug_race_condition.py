#!/usr/bin/env python3
"""
Reproducer for SWATCH-3545 focusing on race conditions and batch processing

This script attempts to reproduce the SWATCH-3545 bug by creating conditions
that might trigger the race condition in conflict resolution:

1. Send events very quickly in succession (simulating Kafka batch)
2. Use different timing patterns
3. Try to trigger the transaction isolation issue

The bug manifests as incorrect deduction values in amendment events.

Usage:
    python3 test_cascade_bug_race_condition.py

Prerequisites:
    - Kafka Bridge running on localhost:9080
    - PostgreSQL running with rhsm-subscriptions database
    - swatch-tally service running to consume Kafka messages
"""

import requests
import psycopg2
import json
import time
import uuid
import threading
from datetime import datetime

# Configuration
KAFKA_BRIDGE_URL = "http://localhost:9080"
TOPIC_NAME = "platform.rhsm-subscriptions.service-instance-ingress"
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

def send_kafka_message(instance_id, cores_value, timestamp, metering_batch_id):
    """Send a message to Kafka via the Bridge API."""
    message = {
        "event_source": "prometheus",
        "event_type": "Cores",
        "org_id": ORG_ID,
        "instance_id": instance_id,
        "metering_batch_id": metering_batch_id,
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
    
    payload = {
        "records": [
            {
                "key": instance_id,
                "value": message
            }
        ]
    }
    
    headers = {"Content-Type": "application/vnd.kafka.json.v2+json"}
    
    try:
        response = requests.post(
            f"{KAFKA_BRIDGE_URL}/topics/{TOPIC_NAME}",
            headers=headers,
            json=payload,
            timeout=10
        )
        response.raise_for_status()
        result = response.json()
        print(f"Sent message with cores={cores_value}, offset={result['offsets'][0]['offset']}")
        return True
    except Exception as e:
        print(f"Failed to send message with cores={cores_value}: {e}")
        return False

def send_message_thread(instance_id, cores_value, timestamp, metering_batch_id):
    """Send a message in a separate thread."""
    return send_kafka_message(instance_id, cores_value, timestamp, metering_batch_id)

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

def test_race_condition_scenario_1():
    """Test scenario 1: Send events very quickly in succession."""
    print("\n=== Race Condition Scenario 1: Quick Succession ===")
    
    # Clear existing events
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send all three events very quickly
    print("Sending events in quick succession...")
    metering_batch_id = None
    
    # Send all events with minimal delay
    send_kafka_message(INSTANCE_ID, 1.0, BASE_TIMESTAMP, metering_batch_id)
    time.sleep(0.01)  # Very small delay
    send_kafka_message(INSTANCE_ID, 10.0, BASE_TIMESTAMP, metering_batch_id)
    time.sleep(0.01)  # Very small delay
    send_kafka_message(INSTANCE_ID, 100.0, BASE_TIMESTAMP, metering_batch_id)
    
    # Wait for processing
    print("Waiting for processing...")
    time.sleep(15)
    
    # Check results
    events = get_events(ORG_ID, INSTANCE_ID)
    print_events(events)
    return analyze_swatch_3545_bug(events)

def test_race_condition_scenario_2():
    """Test scenario 2: Send events in parallel threads."""
    print("\n=== Race Condition Scenario 2: Parallel Threads ===")
    
    # Clear existing events
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send events in parallel threads
    print("Sending events in parallel threads...")
    metering_batch_id = None
    
    threads = []
    cores_values = [1.0, 10.0, 100.0]
    
    for cores_value in cores_values:
        thread = threading.Thread(
            target=send_message_thread,
            args=(INSTANCE_ID, cores_value, BASE_TIMESTAMP, metering_batch_id)
        )
        threads.append(thread)
        thread.start()
    
    # Wait for all threads to complete
    for thread in threads:
        thread.join()
    
    # Wait for processing
    print("Waiting for processing...")
    time.sleep(15)
    
    # Check results
    events = get_events(ORG_ID, INSTANCE_ID)
    print_events(events)
    return analyze_swatch_3545_bug(events)

def test_race_condition_scenario_3():
    """Test scenario 3: Send events with specific timing pattern."""
    print("\n=== Race Condition Scenario 3: Specific Timing ===")
    
    # Clear existing events
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Send events with specific timing that might trigger the bug
    print("Sending events with specific timing pattern...")
    metering_batch_id = None
    
    # First event
    send_kafka_message(INSTANCE_ID, 1.0, BASE_TIMESTAMP, metering_batch_id)
    time.sleep(1)
    
    # Second and third events very close together
    send_kafka_message(INSTANCE_ID, 10.0, BASE_TIMESTAMP, metering_batch_id)
    time.sleep(0.001)  # Very small delay
    send_kafka_message(INSTANCE_ID, 100.0, BASE_TIMESTAMP, metering_batch_id)
    
    # Wait for processing
    print("Waiting for processing...")
    time.sleep(15)
    
    # Check results
    events = get_events(ORG_ID, INSTANCE_ID)
    print_events(events)
    return analyze_swatch_3545_bug(events)

def main():
    """Main function to test different race condition scenarios."""
    print("=== SWATCH-3545 Race Condition Reproducer ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    bug_detected = False
    
    # Test different scenarios
    scenarios = [
        test_race_condition_scenario_1,
        test_race_condition_scenario_2,
        test_race_condition_scenario_3
    ]
    
    for i, scenario in enumerate(scenarios, 1):
        print(f"\n{'='*60}")
        print(f"Testing Scenario {i}")
        print(f"{'='*60}")
        
        try:
            if scenario():
                bug_detected = True
                print(f"\n🎯 SUCCESS: SWATCH-3545 bug reproduced in scenario {i}!")
                break
        except Exception as e:
            print(f"Error in scenario {i}: {e}")
            continue
    
    if not bug_detected:
        print(f"\n✅ No SWATCH-3545 bug detected in any scenario.")
        print("The race condition may have been fixed or requires different conditions.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc() 