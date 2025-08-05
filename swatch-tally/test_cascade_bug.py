#!/usr/bin/env python3
"""
Test script to reproduce the "cascading deductions" bug using Kafka Bridge API.

This script is located in the swatch-tally directory and is used to test the
cascading deductions fix implemented in EventConflictResolver.

The script sends multiple conflicting events with the same instance_id and timestamp
to trigger the transaction isolation issue. With the fix in place, this should
now process events correctly without creating cascading deductions.

Usage:
    python3 test_cascade_bug.py

Prerequisites:
    - Kafka Bridge running on localhost:9080
    - PostgreSQL running with rhsm-subscriptions database
    - swatch-tally service running to consume Kafka messages

Expected behavior (after fix):
    - Only 1 event should be saved (the highest value: 4.0)
    - No deduction events should be created
    - Total events: 1

Bug behavior (before fix):
    - Multiple events would be saved: [2.0, -2.0, 3.0, -3.0, 4.0]
    - Deduction events would be created incorrectly
    - Total events: 5
"""

import requests
import psycopg2
import json
import time
import uuid
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

# Test data
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

def send_kafka_message(instance_id, vcpu_value, timestamp, metering_batch_id):
    """Send a message to Kafka via the Bridge API."""
    message = {
        "event_source": "rhelemeter",
        "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
        "org_id": ORG_ID,
        "instance_id": instance_id,
        "metering_batch_id": metering_batch_id,
        "service_type": "RHEL System",
        "timestamp": timestamp,
        "expiration": BASE_EXPIRATION,
        "display_name": "ip-10-31-97-115.us-east-1.aws.redhat.com",
        "measurements": [{"value": vcpu_value, "metric_id": "vCPUs"}],
        "product_ids": ["69", "204"],
        "sla": "Premium",
        "usage": "Production",
        "billing_provider": "aws",
        "billing_account_id": "746157280291",
        "product_tag": ["rhel-for-x86-els-payg"],
        "conversion": True,
        "isHypervisor": False,
        "isVirtual": False,
        "isUnmappedGuest": False
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
        print(f"Sent message with vCPU={vcpu_value}, offset={result['offsets'][0]['offset']}")
        return True
    except Exception as e:
        print(f"Failed to send message with vCPU={vcpu_value}: {e}")
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
                    data->>'record_date' as record_date
                FROM events 
                WHERE data->>'org_id' = %s AND data->>'instance_id' = %s
                ORDER BY data->>'record_date'
            """, (org_id, instance_id))
            return cur.fetchall()

def analyze_events(events):
    """Analyze events to detect cascading deductions."""
    if not events:
        print("No events found")
        return False
    
    print(f"\n=== Found {len(events)} events ===")
    
    # Group by timestamp to find conflicts
    timestamp_groups = {}
    for event in events:
        timestamp = event[1]  # timestamp column
        if timestamp not in timestamp_groups:
            timestamp_groups[timestamp] = []
        timestamp_groups[timestamp].append(event)
    
    # Check for conflicts (multiple events with same timestamp)
    conflicts = {ts: events for ts, events in timestamp_groups.items() if len(events) > 1}
    
    if conflicts:
        print(f"Found {len(conflicts)} timestamp conflicts:")
        for timestamp, events in conflicts.items():
            print(f"  Timestamp {timestamp}: {len(events)} events")
            for event in events:
                measurements = event[3] if event[3] else []
                vcpu_value = measurements[0]['value'] if measurements else 'N/A'
                print(f"    - Event {event[0][:8]}... vCPU: {vcpu_value}")
    
    # Check for deduction events (negative values)
    deduction_events = []
    for event in events:
        measurements = event[3] if event[3] else []
        if measurements and measurements[0]['value'] < 0:
            deduction_events.append(event)
    
    # Check for positive events (the actual events we expect)
    positive_events = []
    for event in events:
        measurements = event[3] if event[3] else []
        if measurements and measurements[0]['value'] > 0:
            positive_events.append(event)
    
    print(f"\nFound {len(positive_events)} positive events and {len(deduction_events)} deduction events")
    
    if deduction_events:
        print(f"\nFound {len(deduction_events)} deduction events:")
        for event in deduction_events:
            measurements = event[3]
            vcpu_value = measurements[0]['value']
            print(f"  - Event {event[0][:8]}... vCPU: {vcpu_value} (timestamp: {event[1]})")
        
        # This indicates the bug is present (before fix)
        print("\n🚨 CASCADING DEDUCTIONS BUG DETECTED! 🚨")
        print("Multiple deduction events found - this indicates the transaction isolation issue.")
        print("This should not happen with the batch processing fix in place.")
        return True
    elif positive_events:
        # Check if we have the expected single event with highest value
        if len(positive_events) == 1:
            measurements = positive_events[0][3]
            vcpu_value = measurements[0]['value']
            if vcpu_value == 4.0:
                print(f"\n✅ CORRECT BEHAVIOR: Single event with highest value ({vcpu_value}) saved")
                print("Batch processing fix is working correctly - no cascading deductions")
                return False
            else:
                print(f"\n⚠️  UNEXPECTED: Single event saved but wrong value ({vcpu_value}, expected 4.0)")
                return True
        else:
            print(f"\n⚠️  UNEXPECTED: {len(positive_events)} positive events saved (expected 1)")
            return True
    else:
        print("\n❌ NO EVENTS FOUND: Service may not have processed events or there's an error")
        print("This could indicate:")
        print("  - Service not running or not consuming Kafka messages")
        print("  - Events not being saved due to an error")
        print("  - Batch processing logic preventing all events from being saved")
        return True  # Treat as failure since we expect at least one event

def test_cascade_bug():
    """Main test function to reproduce the cascading deductions bug."""
    print("=== Testing Cascading Deductions Bug ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    # Clear existing test events
    print("\n1. Clearing existing test events...")
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Generate a unique batch ID for this test
    metering_batch_id = str(uuid.uuid4())
    
    # Send multiple conflicting messages in quick succession
    print("\n2. Sending conflicting messages via Kafka Bridge API...")
    
    # Send 3 messages with different vCPU values but same timestamp
    vcpu_values = [2.0, 3.0, 4.0]  # Different values to create conflicts
    success_count = 0
    
    for i, vcpu_value in enumerate(vcpu_values):
        print(f"Sending message {i+1}/3 with vCPU={vcpu_value}...")
        if send_kafka_message(INSTANCE_ID, vcpu_value, BASE_TIMESTAMP, metering_batch_id):
            success_count += 1
        time.sleep(0.1)  # Small delay to ensure they're processed together
    
    print(f"Successfully sent {success_count}/{len(vcpu_values)} messages")
    
    if success_count == 0:
        print("❌ Failed to send any messages. Check Kafka Bridge connectivity.")
        return False
    
    # Wait for processing
    print("\n3. Waiting for event processing...")
    time.sleep(10)  # Give more time for Kafka consumer to process
    
    # Check results
    print("\n4. Analyzing results...")
    events = get_events(ORG_ID, INSTANCE_ID)
    bug_detected = analyze_events(events)
    
    return bug_detected

if __name__ == "__main__":
    try:
        bug_detected = test_cascade_bug()
        if bug_detected:
            print("\n🎯 SUCCESS: Cascading deductions bug reproduced!")
            print("This confirms the transaction isolation issue in EventConflictResolver.")
            print("This should not happen with the batch processing fix in place.")
        else:
            print("\n✅ SUCCESS: Batch processing fix is working correctly!")
            print("No cascading deductions detected - the fix is preventing the bug.")
    except Exception as e:
        print(f"\n💥 ERROR: Test failed with exception: {e}")
        import traceback
        traceback.print_exc()
