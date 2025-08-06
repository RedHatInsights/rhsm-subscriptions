#!/bin/bash

# Test script for production-like docker-compose environment
# This tests the SWATCH-3545 bug with true multi-broker Kafka cluster

set -e

echo "🔥 PRODUCTION-LIKE DOCKER-COMPOSE TEST for SWATCH-3545 Bug"
echo "=========================================================="

# Configuration
COMPOSE_FILE="docker-compose-production.yml"
TEST_ORG_ID="production-docker-test-$(date +%s)"
TEST_TIMESTAMP="2025-08-05T23:00:00Z"
TEST_INSTANCE="production-critical-$(date +%s)"

echo "📋 Production-like Environment Features:"
echo "  - 3-broker Kafka cluster (true replication)"
echo "  - Production PostgreSQL settings"
echo "  - Production swatch-tally image and configuration"
echo "  - Exact production resource limits and environment variables"
echo "  - Multi-broker network latency and coordination"
echo ""

# Function to start the production environment
start_production_environment() {
    echo "🚀 Starting production-like environment..."
    
    # Stop any existing containers
    echo "🧹 Stopping existing containers..."
    docker-compose -f $COMPOSE_FILE down -v 2>/dev/null || true
    
    # Start the production environment
    echo "📋 Starting production docker-compose..."
    docker-compose -f $COMPOSE_FILE up -d
    
    echo "⏳ Waiting for services to be ready..."
    
    # Wait for PostgreSQL
    echo "📋 Waiting for PostgreSQL..."
    for i in {1..60}; do
        if docker exec swatch-postgres-production pg_isready -U postgres > /dev/null 2>&1; then
            echo "✅ PostgreSQL is ready"
            break
        fi
        
        if [ $i -eq 60 ]; then
            echo "❌ PostgreSQL failed to start within 2 minutes"
            return 1
        fi
        
        sleep 2
    done
    
    # Wait for Kafka cluster
    echo "📋 Waiting for Kafka cluster..."
    for i in {1..120}; do
        if docker exec swatch-kafka-broker-1 kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
            echo "✅ Kafka cluster is ready"
            break
        fi
        
        if [ $i -eq 120 ]; then
            echo "❌ Kafka cluster failed to start within 4 minutes"
            return 1
        fi
        
        sleep 2
    done
    
    # Wait for swatch-tally
    echo "📋 Waiting for swatch-tally..."
    for i in {1..120}; do
        if curl -s "http://localhost:8000/health" > /dev/null 2>&1; then
            echo "✅ swatch-tally is ready"
            break
        fi
        
        if [ $i -eq 120 ]; then
            echo "❌ swatch-tally failed to start within 4 minutes"
            return 1
        fi
        
        sleep 2
    done
    
    echo "🎉 Production environment is ready!"
}

# Function to run the SWATCH-3545 bug test
run_production_bug_test() {
    echo ""
    echo "🎯 Running SWATCH-3545 bug test with production environment..."
    echo "This includes:"
    echo "  - True 3-broker Kafka cluster with replication"
    echo "  - Production database settings and connection pooling"
    echo "  - Production swatch-tally image with exact configuration"
    echo "  - Multi-broker network latency and coordination"
    echo "  - Real cluster consensus and leader election"
    echo ""
    
    # Send baseline event
    echo "📤 Sending baseline event..."
    curl -s -X POST "http://localhost:8000/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "[{
            \"org_id\": \"$TEST_ORG_ID\",
            \"instance_id\": \"$TEST_INSTANCE\",
            \"timestamp\": \"$TEST_TIMESTAMP\",
            \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
            \"event_source\": \"production-baseline\",
            \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": 1.0}],
            \"product_tag\": [\"rhel-for-x86-els-payg\"],
            \"service_type\": \"RHEL System\",
            \"display_name\": \"production-test\",
            \"sla\": \"Premium\",
            \"usage\": \"Production\",
            \"billing_provider\": \"aws\",
            \"billing_account_id\": \"746157280291\"
        }]" > /dev/null
    
    sleep 3
    
    # Rapid-fire events to trigger the bug
    echo "📤 Sending 100 rapid-fire events to trigger multi-broker coordination..."
    local pids=()
    
    for i in {1..100}; do
        local value=$(echo "scale=1; $i * 2" | bc)
        
        {
            curl -s -X POST "http://localhost:8000/api/rhsm-subscriptions/v1/internal/rpc/tally/events" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "[{
                    \"org_id\": \"$TEST_ORG_ID\",
                    \"instance_id\": \"$TEST_INSTANCE\",
                    \"timestamp\": \"$TEST_TIMESTAMP\",
                    \"event_type\": \"snapshot_rhel-for-x86-els-payg_vcpus\",
                    \"event_source\": \"production-rapid-$i\",
                    \"measurements\": [{\"metric_id\": \"vCPUs\", \"value\": $value}],
                    \"product_tag\": [\"rhel-for-x86-els-payg\"],
                    \"service_type\": \"RHEL System\",
                    \"display_name\": \"production-test\",
                    \"sla\": \"Premium\",
                    \"usage\": \"Production\",
                    \"billing_provider\": \"aws\",
                    \"billing_account_id\": \"746157280291\"
                }]" > /dev/null 2>&1
        } &
        pids+=($!)
        
        # Variable delay to simulate real production timing
        sleep 0.$(($RANDOM % 15 + 1))  # 1-15ms delay
    done
    
    # Wait for all requests
    for pid in "${pids[@]}"; do
        wait $pid 2>/dev/null || true
    done
    
    echo "✅ Rapid-fire test completed"
    
    # Wait for processing
    echo "⏳ Waiting 30 seconds for event processing with multi-broker coordination..."
    sleep 30
}

# Function to check results
check_production_results() {
    echo ""
    echo "🔍 VERIFICATION: Checking for production environment bug..."
    
    # Check total events
    local total_events=$(docker exec swatch-postgres-production psql -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
    SELECT COUNT(*)
    FROM events
    WHERE org_id = '$TEST_ORG_ID'
      AND timestamp = '$TEST_TIMESTAMP'
      AND instance_id = '$TEST_INSTANCE';" 2>/dev/null | tr -d ' ')
    
    # Check deduction count
    local deduction_count=$(docker exec swatch-postgres-production psql -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
    SELECT COUNT(*)
    FROM events
    WHERE org_id = '$TEST_ORG_ID'
      AND timestamp = '$TEST_TIMESTAMP'
      AND instance_id = '$TEST_INSTANCE'
      AND data->>'amendment_type' = 'deduction';" 2>/dev/null | tr -d ' ')
    
    # Check unique values
    local unique_values=$(docker exec swatch-postgres-production psql -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
    SELECT COUNT(DISTINCT data->'measurements'->0->>'value')
    FROM events
    WHERE org_id = '$TEST_ORG_ID'
      AND timestamp = '$TEST_TIMESTAMP'
      AND instance_id = '$TEST_INSTANCE';" 2>/dev/null | tr -d ' ')
    
    echo "📊 Production Test Results:"
    echo "  - Total events: $total_events"
    echo "  - Deduction count: $deduction_count"
    echo "  - Unique values: $unique_values"
    
    echo ""
    echo "🔍 Checking for duplicate deductions (BUG INDICATOR):"
    
    local duplicates=$(docker exec swatch-postgres-production psql -U rhsm-subscriptions -d rhsm-subscriptions -t -c "
    SELECT deduction_value, COUNT(*) as duplicate_count
    FROM (
      SELECT data->'measurements'->0->>'value' as deduction_value
      FROM events
      WHERE org_id = '$TEST_ORG_ID'
        AND timestamp = '$TEST_TIMESTAMP'
        AND instance_id = '$TEST_INSTANCE'
        AND data->>'amendment_type' = 'deduction'
    ) deductions
    GROUP BY deduction_value
    HAVING COUNT(*) > 1;" 2>/dev/null)
    
    if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
        echo "🐛 BUG DETECTED! Duplicate deduction values found:"
        echo "$duplicates"
        echo ""
        echo "🎉 SUCCESS: SWATCH-3545 bug reproduced with production environment!"
        echo "   This confirms the bug requires true multi-broker Kafka cluster behavior!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Even with true production environment, the bug wasn't reproduced locally"
        echo "   This suggests the bug requires additional production-specific conditions"
    fi
}

# Function to show cluster status
show_cluster_status() {
    echo ""
    echo "📋 Kafka Cluster Status:"
    echo "========================"
    
    echo "📊 Topic Configuration:"
    docker exec swatch-kafka-broker-1 kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic platform.rhsm-subscriptions.service-instance-ingress
    
    echo ""
    echo "📊 Broker Status:"
    docker exec swatch-kafka-broker-1 kafka-broker-api-versions.sh --bootstrap-server localhost:9092
    
    echo ""
    echo "📊 Cluster Metadata:"
    docker exec swatch-kafka-broker-1 kafka-cluster.sh cluster-id --bootstrap-server localhost:9092
}

# Function to cleanup
cleanup() {
    echo ""
    echo "🧹 Cleaning up production environment..."
    docker-compose -f $COMPOSE_FILE down -v
    echo "✅ Cleanup complete"
}

# Main execution
case "${1:-test}" in
    "start")
        start_production_environment
        ;;
    "test")
        start_production_environment
        run_production_bug_test
        check_production_results
        show_cluster_status
        ;;
    "status")
        show_cluster_status
        ;;
    "cleanup")
        cleanup
        ;;
    *)
        echo "Usage: $0 [start|test|status|cleanup]"
        echo ""
        echo "Commands:"
        echo "  start   - Start the production-like environment"
        echo "  test    - Start environment and run SWATCH-3545 bug test"
        echo "  status  - Show Kafka cluster status"
        echo "  cleanup - Stop and cleanup all containers"
        echo ""
        echo "🎯 This tests with TRUE production environment:"
        echo "   - 3-broker Kafka cluster with replication"
        echo "   - Production PostgreSQL settings"
        echo "   - Production swatch-tally image"
        echo "   - Multi-broker coordination and network latency"
        exit 1
        ;;
esac 