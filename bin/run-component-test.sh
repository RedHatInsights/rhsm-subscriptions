#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   bin/run-component-test.sh [service-module] [test-fqn]
# Defaults:
#   service-module = swatch-producer-azure
#   test-fqn       = com.redhat.swatch.component.tests.SwatchAzureProducerIT

SERVICE_MODULE="${1:-swatch-producer-azure}"
TEST_FQN="${2:-com.redhat.swatch.component.tests.SwatchAzureProducerIT}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "[1/3] Building local test framework (and deps) ..."
./mvnw -pl :swatch-test-framework -am -DskipTests install

echo "[2/3] Ensuring containers are up ..."
# Detect engine and compose command
ENGINE=""
COMPOSE_CMD=""
if command -v podman >/dev/null 2>&1 && command -v podman-compose >/dev/null 2>&1; then
  ENGINE="podman"
  COMPOSE_CMD="podman-compose"
elif command -v podman >/dev/null 2>&1 && podman version >/dev/null 2>&1; then
  ENGINE="podman"
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    COMPOSE_CMD="podman compose"
  fi
elif command -v docker >/dev/null 2>&1; then
  ENGINE="docker"
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    COMPOSE_CMD="docker compose"
  fi
else
  echo "ERROR: Neither podman nor docker is installed." >&2
  exit 1
fi

# Check if compose stack is running; if not, start it detached
STACK_UP=0
case "$COMPOSE_CMD" in
  "podman compose")
    if podman ps --format '{{.Names}}' | grep -q 'kafka\|zookeeper\|postgres\|wiremock'; then
      STACK_UP=1
    fi
    ;;
  "docker compose"|"docker-compose")
    if docker ps --format '{{.Names}}' | grep -q 'kafka\|zookeeper\|postgres\|wiremock'; then
      STACK_UP=1
    fi
    ;;
  podman-compose)
    # Fallback heuristic using podman ps
    if podman ps --format '{{.Names}}' | grep -q 'kafka\|zookeeper\|postgres\|wiremock'; then
      STACK_UP=1
    fi
    ;;
esac

if [ "$STACK_UP" -eq 0 ]; then
  echo "Starting compose stack with $COMPOSE_CMD ..."
  # Prefer project compose file if present
  if [ -f docker-compose.yml ]; then
    $COMPOSE_CMD -f docker-compose.yml up -d
  else
    $COMPOSE_CMD up -d
  fi
else
  echo "Compose stack appears to be running."
fi

echo "[3/3] Running test $TEST_FQN in module $SERVICE_MODULE ..."
./mvnw -pl :"$SERVICE_MODULE" -am \
  -Dtest="$TEST_FQN" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "Done."


