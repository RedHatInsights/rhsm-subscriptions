#!/bin/bash
set -e

# Wrapper script to combine Java options at runtime
# This allows USER_OPTS_APPEND to be set at runtime while preserving
# the Quarkus and internal options defined at build time.

# Combine all OPTS variables into JAVA_OPTS_APPEND at runtime
# If JAVA_OPTS_APPEND is already set externally, preserve it and add USER_OPTS_APPEND
if [ -z "${JAVA_OPTS_APPEND:-}" ]; then
  # Not set externally - build from component parts
  JAVA_OPTS_APPEND="${QUARKUS_OPTS_APPEND:-} ${INTERNAL_OPTS_APPEND:-} ${USER_OPTS_APPEND:-}"
else
  # Already set externally - just append USER_OPTS_APPEND to it
  JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND} ${USER_OPTS_APPEND:-}"
fi

# Remove leading/trailing whitespace
JAVA_OPTS_APPEND=$(echo "$JAVA_OPTS_APPEND" | xargs)

# Export for run-java.sh to pick up
export JAVA_OPTS_APPEND

# Execute the base image's startup script
exec /opt/jboss/container/java/run/run-java.sh
