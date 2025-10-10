#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -emEuo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR

# Script to test all services or specific services
# Usage: ./scripts/test-all-services.sh [service1 service2 ...]
# If no services are specified, all services will be tested

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
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

# Function to get port for a service
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

# If specific services are provided as arguments, use them
if [ $# -gt 0 ]; then
    SERVICES_TO_TEST=()
    for service_name in "$@"; do
        port=$(get_service_port "$service_name")
        if [ -n "$port" ]; then
            SERVICES_TO_TEST+=("$service_name:$port")
        else
            echo "⚠️  Warning: Service '$service_name' not found in known services list. Skipping."
        fi
    done

    if [ ${#SERVICES_TO_TEST[@]} -eq 0 ]; then
        echo "❌ No valid services specified. Exiting."
        exit 1
    fi

    echo "🎯 Testing specified services: $(printf '%s ' "$@")"
else
    SERVICES_TO_TEST=("${ALL_SERVICES[@]}")
    echo "🚀 Testing all services..."
fi

echo "════════════════════════════════════════"

FAILED_SERVICES=()
PASSED_SERVICES=()

for service_info in "${SERVICES_TO_TEST[@]}"; do
    IFS=':' read -r service_name management_port <<< "$service_info"

    echo ""
    echo "Testing $service_name..."
    echo "----------------------------------------"

    if "$SCRIPT_DIR/test-service-health.sh" "$service_name" "$management_port"; then
        PASSED_SERVICES+=("$service_name")
        echo "✅ $service_name: PASSED"
    else
        FAILED_SERVICES+=("$service_name")
        echo "❌ $service_name: FAILED"
    fi

    echo "----------------------------------------"
done

echo ""
echo "════════════════════════════════════════"
echo "📊 SUMMARY:"
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
    echo ""
    echo "💡 To test a specific service:"
    echo "   ./scripts/test-service-health.sh <service-name> <management-port>"
    exit 1
fi

echo ""
echo "🎉 All services passed health checks!"
