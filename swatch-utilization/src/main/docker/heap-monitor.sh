#!/usr/bin/env bash
# Heap dump monitor
# Monitors container memory and captures heap dumps when threshold exceeded.
# Compresses dumps with gzip and uploads to S3 if configured.
#
# Runs as a background process alongside the JVM (started by entrypoint-wrapper.sh).

POLL_INTERVAL_SEC=10
COMPRESSION_TIMEOUT_SEC=60
RESET_PERCENT=60
RESET_MINUTES=5

HEAP_DUMP_UPLOAD_S3="${HEAP_DUMP_UPLOAD_S3:-true}"
THRESHOLD_PERCENT="${HEAP_MONITOR_THRESHOLD_PERCENT:-75}"
PERIODIC_INTERVAL="${HEAP_MONITOR_PERIODIC_INTERVAL:-900}"
METRICS_PORT="${HEAP_MONITOR_METRICS_PORT:-9000}"
POD_NAME="${HOSTNAME}"

# --- S3 configuration ---

S3_ENABLED=false
S3_PREFIX="${HEAP_DUMP_S3_PREFIX:-swatch-utilization/heapdumps}"

if [ "$HEAP_DUMP_UPLOAD_S3" = "true" ]; then
  # Only check for mounted secret (stage/prod)
  if [ -f /aws/aws_access_key_id ]; then
    export AWS_ACCESS_KEY_ID=$(cat /aws/aws_access_key_id)
    export AWS_SECRET_ACCESS_KEY=$(cat /aws/aws_secret_access_key)
    S3_BUCKET=$(cat /aws/bucket)
    export AWS_DEFAULT_REGION=$(cat /aws/aws_region 2>/dev/null || echo "us-east-1")
    S3_ENABLED=true
    echo "[heap-monitor] S3 upload enabled (bucket: ${S3_BUCKET})"
  else
    echo "[heap-monitor] S3 upload disabled: secret not mounted (ephemeral - use 'oc cp' to retrieve dumps)"
  fi
else
  echo "[heap-monitor] S3 upload disabled by configuration"
fi

# --- Thresholds ---

# Wait for cgroup memory limit to be readable (container may be starting up)
MEMORY_LIMIT_BYTES=0
for i in {1..30}; do
  MEMORY_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.max 2>/dev/null || echo "0")
  if [ "$MEMORY_LIMIT_BYTES" -gt 0 ]; then
    break
  fi
  echo "[heap-monitor] Waiting for cgroup memory limit to be readable (attempt $i/30)..."
  sleep 2
done

if [ "$MEMORY_LIMIT_BYTES" -eq 0 ]; then
  echo "[heap-monitor] ERROR: Cannot detect container memory limit after 60s. Monitoring disabled."
  # Don't exit - just sleep forever to keep container running
  while true; do sleep 3600; done
fi

THRESHOLD_BYTES=$((MEMORY_LIMIT_BYTES * THRESHOLD_PERCENT / 100))
RESET_THRESHOLD=$((MEMORY_LIMIT_BYTES * RESET_PERCENT / 100))
CHECKS_PER_PERIODIC=$((PERIODIC_INTERVAL / POLL_INTERVAL_SEC))
RESET_CHECKS=$((RESET_MINUTES * 60 / POLL_INTERVAL_SEC))

echo "[heap-monitor] Started. Container limit: ${MEMORY_LIMIT_BYTES} bytes, threshold: ${THRESHOLD_BYTES} bytes (${THRESHOLD_PERCENT}%), reset: ${RESET_PERCENT}%"

upload_file_to_s3() {
  local file="$1"
  [ -f "$file" ] || return 0
  local fname s3_path
  fname=$(basename "$file")
  s3_path="s3://${S3_BUCKET}/${S3_PREFIX}/${POD_NAME}/${fname}"
  aws s3 cp "$file" "$s3_path" --quiet || \
    echo "[heap-monitor] ERROR: Failed to upload ${fname}"
}

compress_dump() {
  local dump_file="$1"
  local dump_size
  dump_size=$(du -h "$dump_file" | cut -f1)
  echo "[heap-monitor] Heap dump created: ${dump_file} (${dump_size})"

  echo "[heap-monitor] Compressing heap dump..."
  gzip -c "$dump_file" > "${dump_file}.gz" &
  local gzip_pid=$!

  local elapsed=0
  while kill -0 "$gzip_pid" 2>/dev/null; do
    if [ "$elapsed" -ge "$COMPRESSION_TIMEOUT_SEC" ]; then
      echo "[heap-monitor] ERROR: Compression timed out after ${COMPRESSION_TIMEOUT_SEC}s, killing gzip"
      kill "$gzip_pid" 2>/dev/null
      wait "$gzip_pid" 2>/dev/null
      rm -f "${dump_file}.gz"
      return 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  wait "$gzip_pid"
  local gzip_exit=$?
  if [ "$gzip_exit" -ne 0 ] || [ ! -s "${dump_file}.gz" ]; then
    echo "[heap-monitor] ERROR: Compression failed (exit=${gzip_exit})"
    rm -f "${dump_file}.gz"
    return 1
  fi

  local compressed_size
  compressed_size=$(du -h "${dump_file}.gz" | cut -f1)
  echo "[heap-monitor] Compressed to ${dump_file}.gz (${compressed_size})"
  return 0
}

capture_nmt_summary() {
  local output_file="$1"
  if [ -d "/proc/1" ]; then
    echo "--- $(date '+%Y-%m-%d %H:%M:%S') ---" >> "$output_file"
    jcmd 1 VM.native_memory summary >> "$output_file" 2>&1 || \
      echo "[heap-monitor] NMT capture failed" >> "$output_file"
    echo "" >> "$output_file"
  fi
}

capture_jvm_metrics() {
  local output_file="$1"
  local mem_usage
  mem_usage=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.current 2>/dev/null || echo "0")
  echo "--- $(date '+%Y-%m-%d %H:%M:%S') ---" >> "$output_file"
  echo "container_memory_current_bytes: ${mem_usage}" >> "$output_file"
  echo "container_memory_limit_bytes: ${MEMORY_LIMIT_BYTES}" >> "$output_file"
  curl -s "localhost:${METRICS_PORT}/metrics" 2>/dev/null \
    | grep -E 'jvm_memory_used_bytes|jvm_memory_max_bytes|jvm_buffer_' >> "$output_file" \
    || echo "[heap-monitor] Metrics scrape failed" >> "$output_file"
  echo "" >> "$output_file"
}

# Finds JVM OOM dumps created by -XX:+HeapDumpOnOutOfMemoryError
process_jvm_oom_dumps() {
  local jvm_dump dump_name timestamp renamed
  for jvm_dump in /heapdumps/*.hprof; do
    [ -f "$jvm_dump" ] || continue
    # Skip our own monitor-triggered dumps (contain pod name in filename)
    [[ "$jvm_dump" =~ ${POD_NAME} ]] && continue

    dump_name=$(basename "$jvm_dump" .hprof)
    timestamp=$(date +%Y%m%d-%H%M%S)
    renamed="/heapdumps/jvm-oom-${dump_name}-${POD_NAME}-${timestamp}.hprof"

    echo "[heap-monitor] Found JVM OOM dump: ${jvm_dump} (created by -XX:+HeapDumpOnOutOfMemoryError)"
    mv "$jvm_dump" "$renamed"

    if compress_dump "$renamed"; then
      if [ "$S3_ENABLED" = true ]; then
        echo "[heap-monitor] Uploading JVM OOM dump to S3..."
        upload_file_to_s3 "${renamed}.gz"
        rm -f "${renamed}.gz"
      fi
      rm -f "$renamed"
    fi
  done
}

upload_diagnostics() {
  local pod="$1"
  local ts="$2"
  local dump_file="$3"

  if [ "$S3_ENABLED" = true ]; then
    echo "[heap-monitor] Uploading files to S3..."
    upload_file_to_s3 "/heapdumps/nmt-${pod}-${ts}.txt"
    upload_file_to_s3 "/heapdumps/jvm-metrics-${pod}-${ts}.txt"
    upload_file_to_s3 "/heapdumps/nmt-periodic.txt"
    upload_file_to_s3 "/heapdumps/jvm-metrics-periodic.txt"
    upload_file_to_s3 "${dump_file}.gz"
    echo "[heap-monitor] S3 upload complete"

    rm -f "${dump_file}.gz" \
          "/heapdumps/nmt-${pod}-${ts}.txt" \
          "/heapdumps/jvm-metrics-${pod}-${ts}.txt"
  else
    echo "[heap-monitor] Diagnostics saved to /heapdumps/"
    echo "[heap-monitor] Files: heap-${pod}-${ts}.hprof.gz, nmt-${pod}-${ts}.txt, jvm-metrics-${pod}-${ts}.txt"
    echo "[heap-monitor] Retrieve with: oc cp <pod>:/heapdumps/ ./"
  fi

  if [ -f "$dump_file" ]; then
    echo "[heap-monitor] Removing uncompressed dump to save space..."
    rm -f "$dump_file"
  fi
}

# --- Startup ---

process_jvm_oom_dumps

# --- Main loop ---

dump_triggered=false
reset_counter=0
periodic_counter=0

while true; do
  if [ ! -d "/proc/1" ]; then
    sleep 5
    continue
  fi

  mem_usage=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes 2>/dev/null || cat /sys/fs/cgroup/memory.current 2>/dev/null || echo "0")
  mem_percent=$((mem_usage * 100 / MEMORY_LIMIT_BYTES))

  # Periodic capture
  periodic_counter=$((periodic_counter + 1))
  if [ "$periodic_counter" -ge "$CHECKS_PER_PERIODIC" ]; then
    periodic_counter=0
    capture_nmt_summary "/heapdumps/nmt-periodic.txt" || true
    capture_jvm_metrics "/heapdumps/jvm-metrics-periodic.txt" || true
    process_jvm_oom_dumps || true
  fi

  # Reset: allow re-triggering after memory stays below threshold for RESET_MINUTES
  if [ "$dump_triggered" = true ] && [ "$mem_usage" -lt "$RESET_THRESHOLD" ]; then
    reset_counter=$((reset_counter + 1))
    if [ "$reset_counter" -ge "$RESET_CHECKS" ]; then
      echo "[heap-monitor] Memory dropped to ${mem_percent}%. Resetting dump trigger."
      dump_triggered=false
      reset_counter=0
    fi
  else
    reset_counter=0
  fi

  # Threshold exceeded: capture diagnostics
  if [ "$mem_usage" -gt "$THRESHOLD_BYTES" ] && [ "$dump_triggered" = false ]; then
    echo "[heap-monitor] Memory threshold exceeded: ${mem_usage} bytes (${mem_percent}%). Capturing diagnostics..."
    dump_triggered=true
    ts=$(date +%Y%m%d-%H%M%S)

    if [ -d "/proc/1" ]; then
      echo "[heap-monitor] Capturing NMT summary..."
      capture_nmt_summary "/heapdumps/nmt-${POD_NAME}-${ts}.txt" || true

      echo "[heap-monitor] Capturing JVM metrics..."
      capture_jvm_metrics "/heapdumps/jvm-metrics-${POD_NAME}-${ts}.txt" || true

      dump_file="/heapdumps/heap-${POD_NAME}-${ts}.hprof"
      echo "[heap-monitor] Triggering heap dump to ${dump_file}"
      jcmd 1 GC.heap_dump "$dump_file" || echo "[heap-monitor] Failed to trigger dump"

      sleep "$POLL_INTERVAL_SEC"

      if [ -f "$dump_file" ]; then
        if compress_dump "$dump_file"; then
          upload_diagnostics "$POD_NAME" "$ts" "$dump_file" || true
        fi
      else
        echo "[heap-monitor] ERROR: Heap dump file not created"
      fi
    fi
  fi

  sleep "$POLL_INTERVAL_SEC"
done
