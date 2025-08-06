#!/bin/bash

# Script to simulate connection pool pressure at the application level
# This creates many concurrent connections to exhaust the swatch-tally connection pool
# and reproduce the SWATCH-3545 cascading deduction bug

set -e

echo "🏭 Simulating Connection Pool Pressure for SWATCH-3545 Bug Reproduction"
echo "======================================================================"

# Configuration
SWATCH_URL="http://localhost:8002"
API_ENDPOINT="$SWATCH_URL/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
CONCURRENT_CONNECTIONS=${CONCURRENT_CONNECTIONS:-50}  # More than the typical pool size of 10
PRESSURE_DURATION=${PRESSURE_DURATION:-30}           # Seconds to maintain pressure
CRITICAL_TEST_DELAY=${CRITICAL_TEST_DELAY:-5}        # Delay before critical test

# Test data
ORG_ID="13259775"
TIMESTAMP="2025-07-21T17:00:00Z"
TEST_ID=$(date +%s)

echo "📋 Test Configuration:"
echo "  - Concurrent connections: $CONCURRENT_CONNECTIONS"
echo "  - Pressure duration: ${PRESSURE_DURATION}s"
echo "  - API endpoint: $API_ENDPOINT"
echo "  - Test ID: $TEST_ID"
echo ""

# Function to create event JSON
create_event() {
    local instance_id="$1"
    local value="$2"
    local event_source="$3"
    cat <<EOF
[{
  "org_id": "$ORG_ID",
  "instance_id": "$instance_id",
  "timestamp": "$TIMESTAMP",
  "event_type": "snapshot_rhel-for-x86-els-payg_vcpus",
  "event_source": "$event_source",
  "measurements": [{
    "metric_id": "vCPUs",
    "value": $value
  }],
  "product_tag": ["rhel-for-x86-els-payg"],
  "service_type": "RHEL System",
  "display_name": "pressure-test-$instance_id",
  "sla": "Premium",
  "usage": "Production",
  "billing_provider": "aws",
  "billing_account_id": "746157280291"
}]
EOF
}

# Function to create a long-running connection that holds database connections
create_connection_pressure() {
    local connection_id="$1"
    local duration="$2"
    
    # Create a unique instance for this pressure connection
    local instance_id="pressure-connection-$connection_id-$TEST_ID"
    local payload=$(create_event "$instance_id" "1.0" "pressure-$connection_id")
    
    {
        echo "🔗 [Connection $connection_id] Starting pressure..."
        
        # Send multiple requests in quick succession to hold connections
        for i in {1..10}; do
            curl -s -X POST "$API_ENDPOINT" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "$payload" > /dev/null 2>&1
            
            # Small delay to keep the connection somewhat active
            sleep 0.1
        done
        
        echo "✅ [Connection $connection_id] Pressure applied"
    } &
}

# Function to send critical test events during connection pressure
send_critical_test_events() {
    local critical_instance="critical-test-$TEST_ID"
    
    echo ""
    echo "🎯 CRITICAL TEST: Sending events during connection pool pressure"
    echo "This should trigger the database connection pooling bug!"
    echo ""
    
    # Send baseline event
    echo "📤 Sending baseline event (value: 1.0)..."
    local baseline_payload=$(create_event "$critical_instance" "1.0" "critical-baseline")
    curl -s -X POST "$API_ENDPOINT" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "$baseline_payload" > /dev/null
    
    sleep 1
    
    # Send multiple concurrent events with same EventKey during connection pressure
    echo "📤 Sending 5 CONCURRENT events with same EventKey during connection pressure..."
    local pids=()
    
    for i in {1..5}; do
        {
            local value=$(echo "scale=1; $i * 10" | bc)
            local payload=$(create_event "$critical_instance" "$value" "critical-concurrent-$i")
            
            echo "🔄 [Critical $i] Sending event (value: $value)..."
            local start_time=$(date +%s.%3N)
            
            curl -s -X POST "$API_ENDPOINT" \
                -H "Content-Type: application/json" \
                -H "x-rh-swatch-psk: placeholder" \
                -d "$payload" > /dev/null 2>&1
            
            local end_time=$(date +%s.%3N)
            local duration=$(echo "scale=3; $end_time - $start_time" | bc)
            echo "✅ [Critical $i] Completed in ${duration}s"
        } &
        pids+=($!)
    done
    
    # Wait for all critical events to complete
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    echo "🔍 Critical test events completed during connection pressure"
    echo "$critical_instance"
}

# Function to verify results
verify_bug_reproduction() {
    local critical_instance="$1"
    
    echo ""
    echo "🔍 VERIFICATION: Checking for cascading deduction bug..."
    echo "Instance to check: $critical_instance"
    echo ""
    
    # Query the database for the critical test instance
    local result=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT 
        COUNT(*) as total_events,
        COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,
        array_agg(data->'measurements'->0->>'value' ORDER BY record_date) as value_sequence
    FROM events
    WHERE org_id = '$ORG_ID'
      AND timestamp = '$TIMESTAMP'
      AND instance_id = '$critical_instance';" 2>/dev/null)
    
    if [[ -n "$result" && "$result" != *"0"* ]]; then
        echo "📊 Database Results:"
        echo "$result"
        echo ""
        
        # Check for duplicate deduction values (bug indicator)
        local duplicates=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
        SELECT deduction_value, COUNT(*) as duplicate_count
        FROM (
          SELECT data->'measurements'->0->>'value' as deduction_value
          FROM events
          WHERE org_id = '$ORG_ID'
            AND timestamp = '$TIMESTAMP'
            AND instance_id = '$critical_instance'
            AND data->>'amendment_type' = 'deduction'
        ) deductions
        GROUP BY deduction_value
        HAVING COUNT(*) > 1;" 2>/dev/null)
        
        if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
            echo "🐛 BUG DETECTED! Duplicate deduction values found:"
            echo "$duplicates"
            echo ""
            echo "🎉 SUCCESS: SWATCH-3545 bug reproduced under connection pool pressure!"
        else
            echo "✅ No duplicate deductions found - system working correctly"
            echo "💡 Try increasing CONCURRENT_CONNECTIONS or PRESSURE_DURATION"
        fi
    else
        echo "❌ No events found for critical test instance"
        echo "💡 The test may not have completed successfully"
    fi
}

# Function to show current connection stats
show_connection_stats() {
    echo "📊 Current PostgreSQL connection stats:"
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT 
        count(*) as current_connections,
        max_val as max_connections,
        round(100.0 * count(*) / max_val, 2) as connection_usage_percent
    FROM pg_stat_activity, 
         (SELECT setting::int as max_val FROM pg_settings WHERE name = 'max_connections') max_conn;" 2>/dev/null || echo "Could not get connection stats"
    echo ""
}

# Main test execution
main() {
    echo "🏁 Starting Connection Pool Pressure Test"
    echo ""
    
    # Show initial connection stats
    show_connection_stats
    
    # Step 1: Create connection pressure
    echo "📍 STEP 1: Creating connection pool pressure with $CONCURRENT_CONNECTIONS connections..."
    local pressure_pids=()
    
    for i in $(seq 1 $CONCURRENT_CONNECTIONS); do
        create_connection_pressure "$i" "$PRESSURE_DURATION" &
        pressure_pids+=($!)
        
        # Small delay to stagger connection creation
        sleep 0.05
    done
    
    echo "✅ $CONCURRENT_CONNECTIONS pressure connections started"
    
    # Wait a bit for pressure to build up
    sleep "$CRITICAL_TEST_DELAY"
    
    # Show connection stats during pressure
    echo ""
    echo "📊 Connection stats during pressure:"
    show_connection_stats
    
    # Step 2: Send critical test events during pressure
    echo "📍 STEP 2: Sending critical test events during connection pressure..."
    local critical_instance=$(send_critical_test_events)
    
    # Step 3: Wait for pressure connections to complete
    echo ""
    echo "📍 STEP 3: Waiting for pressure connections to complete..."
    for pid in "${pressure_pids[@]}"; do
        wait "$pid" 2>/dev/null || true  # Ignore errors from background processes
    done
    
    echo "✅ All pressure connections completed"
    
    # Step 4: Verify results
    sleep 2  # Allow time for all database operations to complete
    verify_bug_reproduction "$critical_instance"
    
    # Show final connection stats
    echo ""
    echo "📊 Final connection stats:"
    show_connection_stats
    
    echo ""
    echo "🎯 CONNECTION POOL PRESSURE TEST COMPLETE!"
    echo ""
    echo "📋 Summary:"
    echo "  - Created $CONCURRENT_CONNECTIONS concurrent connections"
    echo "  - Maintained pressure for ${PRESSURE_DURATION}s"
    echo "  - Sent 5 concurrent events with same EventKey during pressure"
    echo "  - Checked for duplicate deduction values (bug indicator)"
    echo ""
    echo "🔍 If no bug was detected, try:"
    echo "  CONCURRENT_CONNECTIONS=100 PRESSURE_DURATION=60 $0"
}

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo "❌ ERROR: 'bc' command not found. Please install it:"
    echo "   sudo dnf install bc"  # Fedora/RHEL
    exit 1
fi

# Check if swatch-tally is running
if ! curl -s "$SWATCH_URL/actuator/health" > /dev/null; then
    echo "❌ ERROR: swatch-tally is not running on port 8002"
    echo "Please start swatch-tally first:"
    echo "SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 MANAGEMENT_SERVER_PORT=9002 SERVER_PORT=8002 DEV_MODE=true ./mvnw -pl swatch-tally spring-boot:run"
    exit 1
fi

# Run the test
main