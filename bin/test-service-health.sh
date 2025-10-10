#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -emEuo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR

# Script to test service health
# Usage: ./scripts/test-service-health.sh <service-name> <management-port>

SERVICE_NAME="${1}"
MANAGEMENT_PORT="${2}"

if [ -z "$SERVICE_NAME" ] || [ -z "$MANAGEMENT_PORT" ]; then
    echo "❌ Usage: $0 <service-name> <management-port>"
    echo "   Example: $0 swatch-contracts 9011"
    exit 1
fi

HEALTH_URL="http://localhost:${MANAGEMENT_PORT}/health"
MAX_ATTEMPTS=36
WAIT_SECONDS=5

echo "🚀 Testing $SERVICE_NAME (management port $MANAGEMENT_PORT)"

# Start service in background
echo "Starting service: make $SERVICE_NAME"
make "$SERVICE_NAME" &
SERVICE_PID=$!
echo "Started $SERVICE_NAME with PID: $SERVICE_PID"

# Function to cleanup on exit
cleanup() {
    echo "🛑 Stopping $SERVICE_NAME..."
    kill $SERVICE_PID 2>/dev/null || true
    wait $SERVICE_PID 2>/dev/null || true
    echo "✅ $SERVICE_NAME stopped successfully"
}

# Setup trap to cleanup on exit (success or failure)
trap cleanup EXIT

# Wait for service to be healthy
echo "⏳ Waiting for $SERVICE_NAME to be healthy at $HEALTH_URL..."
for i in $(seq 1 $MAX_ATTEMPTS); do
    if curl -f "$HEALTH_URL" 2>/dev/null; then
        echo "✅ $SERVICE_NAME is responding!"
        break
    fi
    if [ $i -eq $MAX_ATTEMPTS ]; then
        echo "❌ $SERVICE_NAME failed to become healthy within $((MAX_ATTEMPTS * WAIT_SECONDS / 60)) minutes"
        exit 1
    fi
    echo "Attempt $i/$MAX_ATTEMPTS: Service not ready yet, waiting $WAIT_SECONDS seconds..."
    sleep $WAIT_SECONDS
done

# Verify health endpoint response
echo "🔍 Checking health endpoint response..."
HEALTH_RESPONSE=$(curl -s "$HEALTH_URL")
echo "Health response: $HEALTH_RESPONSE"

if echo "$HEALTH_RESPONSE" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "✅ $SERVICE_NAME health check passed!"
else
    echo "❌ $SERVICE_NAME health check failed - service not reporting UP status"
    exit 1
fi

echo "🎉 $SERVICE_NAME validation completed successfully!"
