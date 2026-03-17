#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -euo pipefail

# Script to test all services or specific services IN PARALLEL.
# Each service uses a unique port, so all can run simultaneously.
# Usage: ./bin/test-all-services.sh [service1 service2 ...]
# If no services are specified, all services will be tested.

ALL_SERVICES=(
    "swatch-tally:9010"
    "swatch-contracts:9011"
    "swatch-billable-usage:9012"
    "swatch-producer-aws:9013"
    "swatch-producer-azure:9014"
    "swatch-metrics-hbi:9015"
    "swatch-metrics:9016"
    "swatch-system-conduit:9017"
    "swatch-utilization:9018"
    "swatch-api:9019"
)

MAX_PARALLEL=${MAX_PARALLEL:-5}
HEALTH_WAIT_SECONDS=2
HEALTH_MAX_ATTEMPTS=90

get_service_port() {
    local service_name=$1
    for service_info in "${ALL_SERVICES[@]}"; do
        IFS=':' read -r svc_name port <<< "$service_info"
        if [ "$svc_name" = "$service_name" ]; then
            echo "$port"
            return
        fi
    done
    echo ""
}

if [ $# -gt 0 ]; then
    SERVICES_TO_TEST=()
    for service_name in "$@"; do
        port=$(get_service_port "$service_name")
        if [ -n "$port" ]; then
            SERVICES_TO_TEST+=("$service_name:$port")
        else
            echo "Warning: Service '$service_name' not found in known services list. Skipping."
        fi
    done

    if [ ${#SERVICES_TO_TEST[@]} -eq 0 ]; then
        echo "No valid services specified. Exiting."
        exit 1
    fi

    echo "Testing specified services: $(printf '%s ' "$@")"
else
    SERVICES_TO_TEST=("${ALL_SERVICES[@]}")
    echo "Testing all services..."
fi

total=${#SERVICES_TO_TEST[@]}
echo "════════════════════════════════════════"
echo "Launching up to $MAX_PARALLEL services in parallel ($total total)"
echo ""

TMPDIR_BASE=$(mktemp -d)
ALL_MAKE_PIDS=()

cleanup_all() {
    echo ""
    echo "Stopping all remaining services..."
    for pid in "${ALL_MAKE_PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    for pid in "${ALL_MAKE_PIDS[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    rm -rf "$TMPDIR_BASE"
}
trap cleanup_all EXIT

# Polls a single service's health endpoint; writes result to a status file.
# Runs as a background job.
check_health_bg() {
    local service_name=$1
    local management_port=$2
    local status_file="$TMPDIR_BASE/${service_name}.status"
    local health_url="http://localhost:${management_port}/health"

    for i in $(seq 1 $HEALTH_MAX_ATTEMPTS); do
        if curl -sf "$health_url" 2>/dev/null | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            echo "PASSED" > "$status_file"
            return 0
        fi
        sleep $HEALTH_WAIT_SECONDS
    done
    echo "FAILED" > "$status_file"
    return 1
}

FAILED_SERVICES=()
PASSED_SERVICES=()

batch_start=0
while [ $batch_start -lt $total ]; do
    batch_end=$((batch_start + MAX_PARALLEL))
    if [ $batch_end -gt $total ]; then
        batch_end=$total
    fi

    batch_make_pids=()
    batch_health_pids=()
    batch_names=()

    echo "Launching batch: services $((batch_start+1)) to $batch_end of $total"

    for (( idx=batch_start; idx<batch_end; idx++ )); do
        IFS=':' read -r service_name management_port <<< "${SERVICES_TO_TEST[$idx]}"
        svc_log="$TMPDIR_BASE/${service_name}.log"

        QUARKUS_LIVE_RELOAD_ENABLED=false SUSPEND_DEBUG=true make "$service_name" > "$svc_log" 2>&1 &
        make_pid=$!

        ALL_MAKE_PIDS+=("$make_pid")
        batch_make_pids+=("$make_pid")
        batch_names+=("$service_name")

        echo "  Started $service_name (PID: $make_pid, port: $management_port)"

        check_health_bg "$service_name" "$management_port" &
        batch_health_pids+=($!)
    done

    echo "Waiting for all health checks in this batch..."
    for (( i=0; i<${#batch_names[@]}; i++ )); do
        svc_name="${batch_names[$i]}"
        health_pid="${batch_health_pids[$i]}"

        # Wait for the background health check to finish (exit 0 = passed, else failed)
        if wait "$health_pid" 2>/dev/null; then
            echo "  ✅ $svc_name: PASSED"
            PASSED_SERVICES+=("$svc_name")
        else
            echo "  ❌ $svc_name: FAILED"
            echo "     Last 20 lines of log:"
            tail -20 "$TMPDIR_BASE/${svc_name}.log" 2>/dev/null | sed 's/^/     /' || true
            FAILED_SERVICES+=("$svc_name")
        fi
    done

    echo "Stopping batch..."
    for pid in "${batch_make_pids[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    for pid in "${batch_make_pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    echo ""

    batch_start=$batch_end
done

echo "════════════════════════════════════════"
echo "SUMMARY:"
echo "   ✅ Passed: ${#PASSED_SERVICES[@]} services"
echo "   ❌ Failed: ${#FAILED_SERVICES[@]} services"

if [ ${#PASSED_SERVICES[@]} -gt 0 ]; then
    echo ""
    echo "✅ Passed services:"
    for service in "${PASSED_SERVICES[@]}"; do
        echo "   - $service"
    done
fi

if [ ${#FAILED_SERVICES[@]} -gt 0 ]; then
    echo ""
    echo "❌ Failed services:"
    for service in "${FAILED_SERVICES[@]}"; do
        echo "   - $service"
    done
    exit 1
fi

echo ""
echo "All services passed health checks!"
