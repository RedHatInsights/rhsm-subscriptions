#!/usr/bin/env python3
"""
Simple runner script to send events to Kafka Bridge API and read from database.

This script sends multiple events with the same instance_id and timestamp
to the Kafka topic and then reads the results from the database.

Usage:
    python3 test_cascade_bug.py

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
        
        vcpu_value = measurements[0]['value'] if measurements else 'N/A'
        
        print(f"\nEvent {i}:")
        print(f"  Event ID: {event_id}")
        print(f"  Timestamp: {timestamp}")
        print(f"  Instance ID: {instance_id}")
        print(f"  vCPU Value: {vcpu_value}")
        print(f"  Record Date: {record_date}")

def main():
    """Main function to send events and read results."""
    print("=== Event Runner ===")
    print(f"Instance ID: {INSTANCE_ID}")
    print(f"Org ID: {ORG_ID}")
    print(f"Timestamp: {BASE_TIMESTAMP}")
    
    # Clear existing test events
    print("\n1. Clearing existing test events...")
    clear_test_events(ORG_ID, INSTANCE_ID)
    
    # Don't set a batch id, it will be set by the service
    metering_batch_id = None
    
    # Send multiple messages with different vCPU values but same timestamp
    print("\n2. Sending messages via Kafka Bridge API...")
    
    vcpu_values = [2.0, 3.0, 4.0]
    success_count = 0
    
    for i, vcpu_value in enumerate(vcpu_values):
        print(f"Sending message {i+1}/{len(vcpu_values)} with vCPU={vcpu_value}...")
        if send_kafka_message(INSTANCE_ID, vcpu_value, BASE_TIMESTAMP, metering_batch_id):
            success_count += 1
        time.sleep(0.1)  # Small delay to ensure they're processed together
    
    print(f"Successfully sent {success_count}/{len(vcpu_values)} messages")
    
    if success_count == 0:
        print("Failed to send any messages. Check Kafka Bridge connectivity.")
        return
    
    # Wait for processing
    print("\n3. Waiting for event processing...")
    time.sleep(10)  # Give time for Kafka consumer to process
    
    # Read and display results
    print("\n4. Reading events from database...")
    events = get_events(ORG_ID, INSTANCE_ID)
    print_events(events)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
