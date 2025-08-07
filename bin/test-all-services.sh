#!/bin/bash

set -e

# Script to test all services
# Usage: ./scripts/test-all-services.sh

SCRIPT_DIR=$(dirname "$0")
SERVICES=(
    "swatch-tally:9010"
    "swatch-contracts:9011"
    "swatch-billable-usage:9012"
    "swatch-producer-aws:9013"
    "swatch-producer-azure:9014"
    "swatch-metrics-hbi:9015"
    "swatch-metrics:9016"
    "swatch-system-conduit:9017"
)

echo "🚀 Testing all services health..."
echo "════════════════════════════════════════"

FAILED_SERVICES=()
PASSED_SERVICES=()

for service_info in "${SERVICES[@]}"; do
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