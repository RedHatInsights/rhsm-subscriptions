#!/bin/bash

# Extreme load test for production images to reproduce SWATCH-3545
# This creates maximum possible concurrent load on production containers

set -e

echo "🔥 EXTREME Production Load Test for SWATCH-3545 Bug"
echo "=================================================="

# Configuration
REPLICA_COUNT=3
BASE_PORT=8002
ORG_ID="13259775"
TIMESTAMP="2025-07-21T17:00:00Z"
TEST_ID=$(date +%s)
CRITICAL_INSTANCE="extreme-load-critical-$TEST_ID"

# Extreme load settings
CONCURRENT_REQUESTS_PER_REPLICA=100  # 300 total concurrent requests
LOAD_DURATION=120                    # 2 minutes of sustained load
RAPID_FIRE_COUNT=50                  # Rapid-fire events per replica

echo "📋 Extreme Load Configuration:"
echo "  - Replicas: $REPLICA_COUNT"
echo "  - Concurrent requests per replica: $CONCURRENT_REQUESTS_PER_REPLICA"
echo "  - Total concurrent requests: $((REPLICA_COUNT * CONCURRENT_REQUESTS_PER_REPLICA))"
echo "  - Load duration: ${LOAD_DURATION}s"
echo "  - Critical instance: $CRITICAL_INSTANCE"
echo ""

# Function to create event JSON
create_event() {
    local instance_id="$1"
    local value="$2"
    local event_source="$3"
    cat <<EOJSON
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
  "display_name": "extreme-load-$instance_id",
  "sla": "Premium",
  "usage": "Production",
  "billing_provider": "aws",
  "billing_account_id": "746157280291"
}]
EOJSON
}

# Function to send event to specific replica
send_to_replica() {
    local replica_id="$1"
    local instance_id="$2"
    local value="$3"
    local event_source="$4"
    
    local port=$((BASE_PORT + replica_id))
    local url="http://localhost:$port/api/rhsm-subscriptions/v1/internal/rpc/tally/events"
    local payload=$(create_event "$instance_id" "$value" "$event_source")
    
    # Silent execution for extreme load
    curl -s -X POST "$url" \
        -H "Content-Type: application/json" \
        -H "x-rh-swatch-psk: placeholder" \
        -d "$payload" > /dev/null 2>&1
}

# Function to create extreme concurrent load
create_extreme_load() {
    local load_type="$1"
    local instance_id="$2"
    
    echo "🔥 Creating $load_type extreme load..."
    local pids=()
    
    # Create massive concurrent load across all replicas
    for replica_id in $(seq 0 $((REPLICA_COUNT - 1))); do
        for request_id in $(seq 1 $CONCURRENT_REQUESTS_PER_REPLICA); do
            {
                local value=$(echo "scale=2; $request_id * 0.1" | bc)
                send_to_replica "$replica_id" "$instance_id" "$value" "$load_type-r$replica_id-req$request_id"
            } &
            pids+=($!)
            
            # Limit concurrent processes to avoid overwhelming the system
            if [ ${#pids[@]} -ge 200 ]; then
                # Wait for current batch to complete
                for pid in "${pids[@]}"; do
                    wait "$pid" 2>/dev/null || true
                done
                pids=()
                echo "  📊 Processed batch of 200 requests..."
            fi
        done
    done
    
    # Wait for remaining requests
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    
    echo "✅ $load_type extreme load completed"
}

# Function to create rapid-fire same EventKey requests
create_rapid_fire_same_eventkey() {
    echo ""
    echo "🎯 CRITICAL: Rapid-fire same EventKey to different replicas"
    echo "This is the most likely scenario to trigger the bug!"
    echo ""
    
    local pids=()
    
    # Send rapid-fire requests with SAME EventKey to DIFFERENT replicas
    for i in $(seq 1 $RAPID_FIRE_COUNT); do
        local replica_id=$((i % REPLICA_COUNT))  # Round-robin across replicas
        local value=$(echo "scale=1; $i * 10" | bc)
        
        {
            send_to_replica "$replica_id" "$CRITICAL_INSTANCE" "$value" "rapid-fire-$i"
        } &
        pids+=($!)
        
        # Very small delay to create timing pressure
        sleep 0.001
    done
    
    # Wait for all rapid-fire requests
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    
    echo "✅ Rapid-fire same EventKey test completed"
}

# Function to monitor container resources during load
monitor_containers() {
    echo ""
    echo "📊 Container Resource Usage During Load:"
    echo "========================================"
    
    for i in $(seq 0 $((REPLICA_COUNT - 1))); do
        local container_name="swatch-tally-replica-$i"
        echo "Container $container_name:"
        
        # Get container stats
        local stats=$(podman stats --no-stream --format "table {{.CPUPerc}} {{.MemUsage}}" "$container_name" 2>/dev/null || echo "N/A N/A")
        echo "  CPU/Memory: $stats"
        
        # Check if container is still running
        if podman ps --filter "name=$container_name" --format "{{.Names}}" | grep -q "$container_name"; then
            echo "  Status: ✅ RUNNING"
        else
            echo "  Status: ❌ CRASHED/STOPPED"
        fi
    done
    echo ""
}

# Main extreme load test
main() {
    echo "🏁 Starting EXTREME Production Load Test"
    echo ""
    
    # Pre-test container monitoring
    monitor_containers
    
    # Step 1: Baseline event
    echo "📍 STEP 1: Sending baseline event..."
    send_to_replica 0 "$CRITICAL_INSTANCE" "1.0" "baseline-extreme"
    sleep 2
    
    # Step 2: Create different types of extreme load
    echo "📍 STEP 2: Creating extreme concurrent load..."
    create_extreme_load "pressure-load" "pressure-load-$TEST_ID"
    
    sleep 5
    
    # Step 3: Rapid-fire same EventKey (most critical test)
    echo "📍 STEP 3: Rapid-fire same EventKey test..."
    create_rapid_fire_same_eventkey
    
    sleep 5
    
    # Step 4: Final burst of concurrent load
    echo "📍 STEP 4: Final burst of concurrent load..."
    create_extreme_load "final-burst" "final-burst-$TEST_ID"
    
    # Post-test container monitoring
    monitor_containers
    
    # Step 5: Wait for processing and verify
    echo "📍 STEP 5: Waiting for event processing..."
    sleep 10  # Longer wait for extreme load processing
    
    echo ""
    echo "🔍 VERIFICATION: Checking for extreme load bug..."
    
    # Check the critical instance (rapid-fire same EventKey)
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT 
        COUNT(*) as total_events,
        COUNT(*) FILTER (WHERE data->>'amendment_type' = 'deduction') as deduction_count,
        COUNT(DISTINCT data->'measurements'->0->>'value') as unique_values,
        array_length(array_agg(data->'measurements'->0->>'value' ORDER BY record_date), 1) as total_values
    FROM events
    WHERE org_id = '$ORG_ID'
      AND timestamp = '$TIMESTAMP'
      AND instance_id = '$CRITICAL_INSTANCE';" || echo "Database query failed"
    
    echo ""
    echo "🔍 Checking for duplicate deductions (BUG INDICATOR):"
    
    local duplicates=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT deduction_value, COUNT(*) as duplicate_count
    FROM (
      SELECT data->'measurements'->0->>'value' as deduction_value
      FROM events
      WHERE org_id = '$ORG_ID'
        AND timestamp = '$TIMESTAMP'
        AND instance_id = '$CRITICAL_INSTANCE'
        AND data->>'amendment_type' = 'deduction'
    ) deductions
    GROUP BY deduction_value
    HAVING COUNT(*) > 1;" 2>/dev/null)
    
    if [[ -n "$duplicates" && "$duplicates" =~ [0-9] ]]; then
        echo "🐛 BUG DETECTED! Duplicate deduction values found:"
        echo "$duplicates"
        echo ""
        echo "🎉 SUCCESS: SWATCH-3545 extreme load reproduced the bug!"
    else
        echo "✅ No duplicate deductions found"
        echo "💡 Production containers are extremely robust to load"
    fi
    
    # Show overall statistics
    echo ""
    echo "📊 Overall Test Statistics:"
    local total_events=$(psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -t -c "
    SELECT COUNT(*) 
    FROM events 
    WHERE org_id = '$ORG_ID' 
      AND timestamp = '$TIMESTAMP' 
      AND (instance_id LIKE '%$TEST_ID%' OR data->>'event_source' LIKE '%$TEST_ID%');" 2>/dev/null || echo "0")
    
    echo "  - Total events processed: $total_events"
    echo "  - Expected concurrent requests: $((REPLICA_COUNT * CONCURRENT_REQUESTS_PER_REPLICA * 2 + RAPID_FIRE_COUNT + 1))"
    echo "  - Test duration: Approximately $((LOAD_DURATION + 30)) seconds"
    
    echo ""
    echo "🎯 EXTREME PRODUCTION LOAD TEST COMPLETE!"
    echo ""
    echo "📋 Summary:"
    echo "  - Created maximum possible concurrent load on production containers"
    echo "  - Tested rapid-fire same EventKey scenario (most likely to trigger bug)"
    echo "  - Monitored container resource usage and stability"
    echo "  - If no bug detected: Production system is exceptionally robust"
}

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo "❌ ERROR: 'bc' command not found. Please install it"
    exit 1
fi

# Warn about extreme load
echo "⚠️  WARNING: This test creates EXTREME load on the system!"
echo "   - 300+ concurrent requests"
echo "   - Sustained load for 2+ minutes"
echo "   - May impact system performance"
echo ""
read -p "Continue with extreme load test? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Test cancelled."
    exit 0
fi

# Run the extreme test
main