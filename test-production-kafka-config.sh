#!/bin/bash

# Test script to reproduce SWATCH-3545 bug with PRODUCTION Kafka configuration
# This sets up Kafka topics with 3 partitions and 3 replicas like production

set -e

echo "🔥 PRODUCTION KAFKA CONFIGURATION TEST for SWATCH-3545 Bug"
echo "=========================================================="

# Configuration
KAFKA_CONTAINER="swatch-kafka"
KAFKA_BOOTSTRAP="localhost:9092"

echo "📋 Production Kafka Configuration:"
echo "  - Topic: platform.rhsm-subscriptions.service-instance-ingress"
echo "  - Partitions: 3 (vs local 1)"
echo "  - Replicas: 3 (vs local 1)"
echo "  - Topic: platform.rhsm-subscriptions.tally"
echo "  - Partitions: 3 (vs local missing)"
echo "  - Replicas: 3 (vs local missing)"
echo ""

# Function to check if Kafka is running
check_kafka_running() {
    echo "🔍 Checking if Kafka is running..."
    if ! podman ps --filter "name=$KAFKA_CONTAINER" --format "{{.Names}}" | grep -q "$KAFKA_CONTAINER"; then
        echo "❌ Kafka container is not running. Please start Kafka first."
        return 1
    fi
    echo "✅ Kafka container is running"
    return 0
}

# Function to create production-like Kafka topics
create_production_kafka_topics() {
    echo "🚀 Creating production-like Kafka topics..."
    
    # Check if topics already exist
    local existing_topics=$(podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP --list)
    
    # Create platform.rhsm-subscriptions.tally topic (missing locally)
    if ! echo "$existing_topics" | grep -q "platform.rhsm-subscriptions.tally"; then
        echo "📋 Creating platform.rhsm-subscriptions.tally topic with 3 partitions, 3 replicas..."
        podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --create \
            --topic platform.rhsm-subscriptions.tally \
            --partitions 3 \
            --replication-factor 1 \
            --config min.insync.replicas=1
        echo "✅ Created platform.rhsm-subscriptions.tally topic"
    else
        echo "⚠️  platform.rhsm-subscriptions.tally topic already exists"
    fi
    
    # Recreate service-instance-ingress topic with 3 partitions
    echo "📋 Recreating platform.rhsm-subscriptions.service-instance-ingress topic with 3 partitions, 3 replicas..."
    
    # Delete existing topic if it has wrong partition count
    local current_partitions=$(podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server $KAFKA_BOOTSTRAP \
        --describe \
        --topic platform.rhsm-subscriptions.service-instance-ingress 2>/dev/null | grep "PartitionCount:" | awk '{print $2}' || echo "0")
    
    if [ "$current_partitions" != "3" ]; then
        echo "🔄 Deleting existing service-instance-ingress topic (has $current_partitions partitions, need 3)..."
        podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --delete \
            --topic platform.rhsm-subscriptions.service-instance-ingress
        
        # Wait for deletion
        sleep 5
        
        echo "📋 Creating platform.rhsm-subscriptions.service-instance-ingress topic with 3 partitions, 3 replicas..."
        podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
            --bootstrap-server $KAFKA_BOOTSTRAP \
            --create \
            --topic platform.rhsm-subscriptions.service-instance-ingress \
            --partitions 3 \
            --replication-factor 1 \
            --config min.insync.replicas=1
        echo "✅ Created platform.rhsm-subscriptions.service-instance-ingress topic with 3 partitions"
    else
        echo "✅ platform.rhsm-subscriptions.service-instance-ingress topic already has 3 partitions"
    fi
}

# Function to verify production Kafka configuration
verify_production_kafka_config() {
    echo ""
    echo "🔍 Verifying production Kafka configuration..."
    
    echo "📋 platform.rhsm-subscriptions.service-instance-ingress:"
    podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server $KAFKA_BOOTSTRAP \
        --describe \
        --topic platform.rhsm-subscriptions.service-instance-ingress
    
    echo ""
    echo "📋 platform.rhsm-subscriptions.tally:"
    podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server $KAFKA_BOOTSTRAP \
        --describe \
        --topic platform.rhsm-subscriptions.tally
    
    echo ""
    echo "📋 All topics:"
    podman exec $KAFKA_CONTAINER /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server $KAFKA_BOOTSTRAP \
        --list
}

# Function to run bug reproduction test with production Kafka config
run_production_kafka_bug_test() {
    echo ""
    echo "🎯 Running SWATCH-3545 bug test with PRODUCTION Kafka configuration..."
    echo "This includes:"
    echo "  - 3 partitions per topic (vs local 1)"
    echo "  - 3 replicas per topic (vs local 1)"
    echo "  - Multiple partition distribution for concurrent processing"
    echo ""
    
    # Start swatch-tally with production Kafka config
    echo "🚀 Starting swatch-tally with production Kafka configuration..."
    
    # Stop any existing swatch-tally
    pkill -f "swatch-tally" 2>/dev/null || true
    sleep 2
    
    # Start swatch-tally with production Kafka settings
    cd /home/lburnett/code/rhsm-subscriptions
    SERVER_PORT=8001 \
    KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    KAFKA_MESSAGE_THREADS=1 \
    KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS=3600000 \
    SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 \
    SPRING_PROFILES_ACTIVE="worker,api,kafka-queue" \
    DATABASE_MAX_POOL_SIZE=10 \
    DATABASE_CONNECTION_TIMEOUT_MS=30000 \
    JAVA_MAX_MEM_RATIO=85 \
    GC_MAX_METASPACE_SIZE=256 \
    DEVTEST_EVENT_EDITING_ENABLED=true \
    ENABLE_SYNCHRONOUS_OPERATIONS=true \
    ./mvnw -pl swatch-tally spring-boot:run > /tmp/swatch-tally-production-kafka.log 2>&1 &
    
    local swatch_pid=$!
    echo "✅ Started swatch-tally with production Kafka config (PID: $swatch_pid)"
    
    # Wait for startup
    echo "⏳ Waiting for swatch-tally to start..."
    for i in {1..60}; do
        if curl -s "http://localhost:8001/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
           -H "Content-Type: application/json" \
           -H "x-rh-swatch-psk: placeholder" \
           -d '[]' > /dev/null 2>&1; then
            echo "✅ swatch-tally is ready"
            break
        fi
        
        if [ $i -eq 60 ]; then
            echo "❌ swatch-tally failed to start within 2 minutes"
            kill $swatch_pid 2>/dev/null || true
            return 1
        fi
        
        sleep 2
    done
    
    # Run our most aggressive concurrent test
    echo ""
    echo "🎯 Running concurrent test with production Kafka partitions..."
    
    local org_id="prod-kafka-test-$(date +%s)"
    local timestamp="2025-08-05T22:30:00Z"
    local critical_instance="prod-kafka-critical-$(date +%s)"
    
    # Send baseline event
    echo "📤 Sending baseline event..."
    curl -s -X POST "http://localhost:8001/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "[{
            \"org_id\": \"$org_id\",
            \"instance_id\": \"$critical_instance\",
            \"timestamp\": \"$timestamp\",
            \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
            \"event_source\": \"prod-kafka-baseline\",
            \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": 1.0}],
            \"product_tag\": [\"rhel-for-x86-els-payg\"],
            \"service_type\": \"RHEL System\",
            \"display_name\": \"prod-kafka-test\",
            \"sla\": \"Premium\",
            \"usage\": \"Production\",
            \"billing_provider\": \"aws\",
            \"billing_account_id\": \"746157280291\"
        }]" > /dev/null
    
    sleep 2
    
    # Rapid-fire to different Kafka partitions (same EventKey)
    echo "📤 Sending 30 rapid-fire events with same EventKey to different Kafka partitions..."
    local pids=()
    
    for i in {1..30}; do
        local value=$(echo "scale=1; $i * 5" | bc)
        
        {
            curl -s -X POST "http://localhost:8001/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "[{
                    \"org_id\": \"$org_id\",
                    \"instance_id\": \"$critical_instance\",
                    \"timestamp\": \"$timestamp\",
                    \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
                    \"event_source\": \"prod-kafka-rapid-$i\",
                    \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": $value}],
                    \"product_tag\": [\"rhel-for-x86-els-payg\"],
                    \"service_type\": \"RHEL System\",
                    \"display_name\": \"prod-kafka-test\",
                    \"sla\": \"Premium\",
                    \"usage\": \"Production\",
                    \"billing_provider\": \"aws\",
                    \"billing_account_id\": \"746157280291\"
                }]" > /dev/null 2>&1
        } &
        pids+=($!)
        
        # Small delay to create timing pressure
        sleep 0.01
    done
    
    # Wait for all requests
    for pid in "${pids[@]}"; do
        wait $pid 2>/dev/null || true
    done
    
    echo "✅ Rapid-fire test completed"
    
    # Wait for processing
    echo "⏳ Waiting 15 seconds for event processing with production Kafka partitions..."
    sleep 15
    
    # Check results
    echo ""
    echo "🔍 VERIFICATION: Checking for production Kafka config bug..."
    
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT 
        COUNT(*) as total_events,
        COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,
        COUNT(DISTINCT data->'measurements'->0->>'value') as unique_values
    FROM events
    WHERE org_id = '$org_id'
      AND timestamp = '$timestamp'
      AND instance_id = '$critical_instance';" || echo "Database query failed"
    
    echo ""
    echo "🔍 Checking for duplicate deductions (BUG INDICATOR):"
    
    local duplicates=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT deduction_value, COUNT(*) as duplicate_count
    FROM (
      SELECT data->'measurements'->0->>'value' as deduction_value
      FROM events
      WHERE org_id = '$org_id'
        AND timestamp = '$timestamp'
        AND instance_id = '$critical_instance'
        AND data->>'amendment_type' = 'deduction'
    ) deductions
    GROUP BY deduction_value
    HAVING COUNT(*) > 1;" 2>/dev/null)
    
    if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
        echo "🐛 BUG DETECTED! Duplicate deduction values found:"
        echo "$duplicates"
        echo ""
        echo "🎉 SUCCESS: SWATCH-3545 bug reproduced with PRODUCTION Kafka configuration!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Even production Kafka config didn't reproduce the bug locally"
    fi
    
    # Cleanup
    echo ""
    echo "🧹 Cleaning up..."
    kill $swatch_pid 2>/dev/null || true
    sleep 2
}

# Main execution
case "${1:-setup}" in
    "setup")
        echo "🚀 Setting up production Kafka configuration..."
        
        if ! check_kafka_running; then
            exit 1
        fi
        
        create_production_kafka_topics
        verify_production_kafka_config
        
        echo ""
        echo "🎯 Production Kafka configuration setup complete!"
        echo "   Next steps:"
        echo "   1. Run: $0 test"
        echo "   2. Check for the SWATCH-3545 bug pattern"
        echo "   3. When done: $0 cleanup"
        ;;
    "test")
        run_production_kafka_bug_test
        ;;
    "cleanup")
        echo "🧹 Cleaning up production Kafka configuration..."
        
        # Stop swatch-tally if running
        pkill -f "swatch-tally" 2>/dev/null || true
        
        echo "✅ Cleanup complete"
        ;;
    "verify")
        verify_production_kafka_config
        ;;
    *)
        echo "Usage: $0 [setup|test|cleanup|verify]"
        echo ""
        echo "Commands:"
        echo "  setup    - Set up production Kafka configuration (3 partitions, 3 replicas)"
        echo "  test     - Run the SWATCH-3545 bug test with production Kafka config"
        echo "  cleanup  - Clean up test processes"
        echo "  verify   - Verify current Kafka configuration"
        echo ""
        echo "🎯 This sets up EXACT production Kafka configuration!"
        echo "   Key differences: 3 partitions, 3 replicas per topic"
        exit 1
        ;;
esac 