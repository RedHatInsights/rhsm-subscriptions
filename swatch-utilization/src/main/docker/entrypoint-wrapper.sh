#!/bin/bash
# Entrypoint wrapper that starts heap monitor before JVM

# Start heap monitor in background if enabled
if [ "${ENABLE_HEAP_MONITOR}" = "true" ]; then
  echo "Starting heap monitor..."
  /opt/heap-monitor.sh &
  MONITOR_PID=$!
  echo "Heap monitor started with PID $MONITOR_PID"
fi

# Start the JVM using the standard run-java.sh script
exec /opt/jboss/container/java/run/run-java.sh
