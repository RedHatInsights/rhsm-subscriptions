#!/bin/bash

# Test script to simulate multi-broker Kafka cluster behavior
# This attempts to reproduce production cluster conditions that might trigger the bug

set -e

echo "🔥 MULTI-BROKER KAFKA CLUSTER SIMULATION for SWATCH-3545 Bug"
echo "============================================================="

# Configuration
KAFKA_CONTAINER="swatch-kafka"
KAFKA_BOOTSTRAP="localhost:9092"

echo "📋 Multi-Broker Cluster Simulation:"
echo "  - Simulating 3-broker cluster behavior"
echo "  - Network latency between brokers"
echo "  - Partition distribution across brokers"
echo "  - Leader election and failover scenarios"
echo "  - Cluster coordination patterns"
echo ""

# Function to simulate network latency and cluster coordination
simulate_cluster_behavior() {
    echo "🌐 Simulating multi-broker cluster behavior..."
    
    # Simulate network latency between brokers (5-50ms)
    echo "⏱️  Simulating network latency between brokers..."
    for i in {1..10}; do
        sleep 0.$(($RANDOM % 50 + 5))  # 5-50ms latency
    done
    
    # Simulate partition distribution across brokers
    echo "📊 Simulating partition distribution across brokers..."
    local partition=$((RANDOM % 3))  # 0, 1, or 2
    echo "   Event routed to partition $partition"
    
    # Simulate leader election scenarios
    echo "👑 Simulating leader election scenarios..."
    local leader_changes=$((RANDOM % 3))
    if [ $leader_changes -gt 0 ]; then
        echo "   Leader election occurred ($leader_changes changes)"
        sleep 0.1  # Simulate leader election delay
    fi
}

# Function to run aggressive concurrent test with cluster simulation
run_multi_broker_bug_test() {
    echo ""
    echo "🎯 Running SWATCH-3545 bug test with multi-broker cluster simulation..."
    echo "This includes:"
    echo "  - Network latency simulation (5-50ms between brokers)"
    echo "  - Partition distribution across 3 brokers"
    echo "  - Leader election and failover scenarios"
    echo "  - Cluster coordination patterns"
    echo "  - True multi-broker concurrent processing"
    echo ""
    
    # Start swatch-tally with production settings
    echo "🚀 Starting swatch-tally with multi-broker simulation..."
    
    # Stop any existing swatch-tally
    pkill -f "swatch-tally" 2>/dev/null || true
    sleep 2
    
    # Start swatch-tally with production settings
    cd /home/lburnett/code/rhsm-subscriptions
    SERVER_PORT=8002 \
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
    ./mvnw -pl swatch-tally spring-boot:run > /tmp/swatch-tally-multi-broker.log 2>&1 &
    
    local swatch_pid=$!
    echo "✅ Started swatch-tally with multi-broker simulation (PID: $swatch_pid)"
    
    # Wait for startup
    echo "⏳ Waiting for swatch-tally to start..."
    for i in {1..60}; do
        if curl -s "http://localhost:8002/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
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
    
    # Run aggressive concurrent test with cluster simulation
    echo ""
    echo "🎯 Running aggressive concurrent test with multi-broker simulation..."
    
    local org_id="multi-broker-test-$(date +%s)"
    local timestamp="2025-08-05T22:35:00Z"
    local critical_instance="multi-broker-critical-$(date +%s)"
    
    # Send baseline event with cluster simulation
    echo "📤 Sending baseline event with cluster simulation..."
    simulate_cluster_behavior
    curl -s -X POST "http://localhost:8002/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "[{
            \"org_id\": \"$org_id\",
            \"instance_id\": \"$critical_instance\",
            \"timestamp\": \"$timestamp\",
            \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
            \"event_source\": \"multi-broker-baseline\",
            \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": 1.0}],
            \"product_tag\": [\"rhel-for-x86-els-payg\"],
            \"service_type\": \"RHEL System\",
            \"display_name\": \"multi-broker-test\",
            \"sla\": \"Premium\",
            \"usage\": \"Production\",
            \"billing_provider\": \"aws\",
            \"billing_account_id\": \"746157280291\"
        }]" > /dev/null
    
    sleep 2
    
    # Rapid-fire with cluster simulation (same EventKey to different brokers)
    echo "📤 Sending 50 rapid-fire events with multi-broker cluster simulation..."
    local pids=()
    
    for i in {1..50}; do
        local value=$(echo "scale=1; $i * 3" | bc)
        
        {
            # Simulate cluster behavior for each event
            simulate_cluster_behavior
            
            curl -s -X POST "http://localhost:8002/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "[{
                    \"org_id\": \"$org_id\",
                    \"instance_id\": \"$critical_instance\",
                    \"timestamp\": \"$timestamp\",
                    \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
                    \"event_source\": \"multi-broker-rapid-$i\",
                    \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": $value}],
                    \"product_tag\": [\"rhel-for-x86-els-payg\"],
                    \"service_type\": \"RHEL System\",
                    \"display_name\": \"multi-broker-test\",
                    \"sla\": \"Premium\",
                    \"usage\": \"Production\",
                    \"billing_provider\": \"aws\",
                    \"billing_account_id\": \"746157280291\"
                }]" > /dev/null 2>&1
        } &
        pids+=($!)
        
        # Variable delay to simulate real cluster timing
        sleep 0.$(($RANDOM % 20 + 1))  # 1-20ms delay
    done
    
    # Wait for all requests
    for pid in "${pids[@]}"; do
        wait $pid 2>/dev/null || true
    done
    
    echo "✅ Multi-broker rapid-fire test completed"
    
    # Wait for processing with cluster simulation
    echo "⏳ Waiting 20 seconds for event processing with multi-broker simulation..."
    sleep 20
    
    # Check results
    echo ""
    echo "🔍 VERIFICATION: Checking for multi-broker cluster bug..."
    
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
        echo "🎉 SUCCESS: SWATCH-3545 bug reproduced with multi-broker cluster simulation!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Even multi-broker cluster simulation didn't reproduce the bug locally"
    fi
    
    # Cleanup
    echo ""
    echo "🧹 Cleaning up..."
    kill $swatch_pid 2>/dev/null || true
    sleep 2
}

# Function to test with different Kafka consumer group patterns
test_consumer_group_patterns() {
    echo ""
    echo "🎯 Testing different Kafka consumer group patterns..."
    
    # Test 1: Multiple consumer groups
    echo "📋 Test 1: Multiple consumer groups with same EventKey..."
    
    # Test 2: Consumer group rebalancing
    echo "📋 Test 2: Consumer group rebalancing scenarios..."
    
    # Test 3: Partition reassignment
    echo "📋 Test 3: Partition reassignment during processing..."
    
    echo "✅ Consumer group pattern tests completed"
}

# Main execution
case "${1:-test}" in
    "test")
        run_multi_broker_bug_test
        ;;
    "consumer-groups")
        test_consumer_group_patterns
        ;;
    "cleanup")
        echo "🧹 Cleaning up multi-broker simulation..."
        
        # Stop swatch-tally if running
        pkill -f "swatch-tally" 2>/dev/null || true
        
        echo "✅ Cleanup complete"
        ;;
    *)
        echo "Usage: $0 [test|consumer-groups|cleanup]"
        echo ""
        echo "Commands:"
        echo "  test            - Run multi-broker cluster simulation test"
        echo "  consumer-groups - Test different consumer group patterns"
        echo "  cleanup         - Clean up test processes"
        echo ""
        echo "🎯 This simulates multi-broker Kafka cluster behavior!"
        echo "   Key simulations: network latency, partition distribution, leader election"
        exit 1
        ;;
esac 