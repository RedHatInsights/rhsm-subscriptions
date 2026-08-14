#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -euo pipefail

# Script to test all services or specific services IN PARALLEL.
# Each service uses a unique port, so all can run simultaneously.
# Usage: ./bin/test-all-services.sh [service1 service2 ...]
# If no services are specified, all services will be tested.
#
# Services in SERIAL_SERVICES are started alone (not in a parallel batch)
# because they are heavier to boot under CI load.

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

# Heavier services: run alone so they are not starved by sibling quarkus:dev JVMs.
SERIAL_SERVICES=(
    "swatch-contracts"
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

is_serial_service() {
    local service_name=$1
    local serial
    for serial in "${SERIAL_SERVICES[@]}"; do
        if [ "$serial" = "$service_name" ]; then
            return 0
        fi
    done
    return 1
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

SERIAL_TO_TEST=()
PARALLEL_TO_TEST=()
for service_info in "${SERVICES_TO_TEST[@]}"; do
    IFS=':' read -r service_name _ <<< "$service_info"
    if is_serial_service "$service_name"; then
        SERIAL_TO_TEST+=("$service_info")
    else
        PARALLEL_TO_TEST+=("$service_info")
    fi
done

total=${#SERVICES_TO_TEST[@]}
echo "════════════════════════════════════════"
echo "Testing $total service(s): ${#SERIAL_TO_TEST[@]} serial, ${#PARALLEL_TO_TEST[@]} parallel (max $MAX_PARALLEL)"
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

# Runs one batch of services (size limited by caller via the list passed).
# Arguments: service_info entries ("name:port" ...).
run_batch() {
    local -a batch_services=("$@")
    local batch_count=${#batch_services[@]}
    if [ "$batch_count" -eq 0 ]; then
        return
    fi

    local batch_make_pids=()
    local batch_health_pids=()
    local batch_names=()
    local service_info service_name management_port svc_log make_pid
    local i svc_name health_pid pid

    echo "Launching batch: $batch_count service(s)"

    for service_info in "${batch_services[@]}"; do
        IFS=':' read -r service_name management_port <<< "$service_info"
        svc_log="$TMPDIR_BASE/${service_name}.log"

        QUARKUS_LIVE_RELOAD_ENABLED=false QUARKUS_UNLEASH_ACTIVE=false SUSPEND_DEBUG=true make "$service_name" > "$svc_log" 2>&1 &
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
}

# Serial services first (alone), then the rest in parallel batches.
if [ ${#SERIAL_TO_TEST[@]} -gt 0 ]; then
    echo "Serial services (one at a time):"
    for service_info in "${SERIAL_TO_TEST[@]}"; do
        run_batch "$service_info"
    done
fi

parallel_total=${#PARALLEL_TO_TEST[@]}
batch_start=0
while [ "$batch_start" -lt "$parallel_total" ]; do
    batch_end=$((batch_start + MAX_PARALLEL))
    if [ "$batch_end" -gt "$parallel_total" ]; then
        batch_end=$parallel_total
    fi

    batch=("${PARALLEL_TO_TEST[@]:$batch_start:$((batch_end - batch_start))}")
    run_batch "${batch[@]}"

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
