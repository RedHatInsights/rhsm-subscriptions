#!/usr/bin/env bash
# https://github.com/olivergondza/bash-strict-mode
set -emEuo pipefail
trap 's=$?; echo >&2 "$0: Error on line "$LINENO": $BASH_COMMAND"; exit $s' ERR
trap cleanup SIGINT SIGHUP SIGTERM EXIT

function cleanup() {
  PROM_FILE=${PROM_FILE:-}
  if [ -n "${PROM_FILE}" ]; then
    rm "${PROM_FILE}"
  fi

  FILE=${FILE:-}
  if [ -n "${FILE}" ]; then
    rm "${FILE}"
  fi

  # Kill the subshell on interrupt if necessary
  local pids=$(jobs -pr)
  if [ -n "$pids" ]; then
    kill $pids
  fi
}

function usage() {
  echo "Usage: $0 [-p port]"
  echo "-c CONTAINER_ID id of container to import to. If not provided, a prometheus pod will be started"
  echo "-p PORT         local port to run prometheus over (default: 9090)"
  echo "-t              import using 'tsdb create-blocks-from' (slower but allows for unlimited time range)"
  echo "-m MOCK_ARGS    arguments to pass to swatchdog for generating mock data. Remember to quote correctly. Can"
  echo "                also be given via environment with SWATCHDOG_ARGS."
  echo "-f FILE         file containing mock data to import. Make sure the format matches the import type you are"
  echo "                using. -t requires an openmetrics type file.  See comments for more details."
}

function import() {
  # An alternate approach to using promtool push metrics is to use the tsdb command to import openmetrics format data.
  # Push is faster but is limited by the setting of out_of_order_time_window.  Anything earlier than the window allows
  # can't be imported.  Create-blocks-from allows for arbitrary times in the past, but it is much slower, and running it
  # after prometheus has already started can have some issues.  Specifically, backfilling data from the last 3 hours
  # (the current head block) can cause problems, as this time range may overlap with the current head block Prometheus
  # is still mutating.  Generally speaking, we shouldn't have a problem here since we aren't actively monitoring live
  # metrics, but it is something to be aware of.
  #
  # Users importing their own mock data file must make sure the file format they are using is supported by the import
  # method they choose.  The `push metrics` method requires the prometheus format (times given in epoch milliseconds)
  # while `tsdb create-blocks-from` uses the openmetrics format (times given as a float of epoch seconds with nanosecond
  # precision supported).
  if [ $TSDB -eq 0 ]; then
    FORMAT="--prometheus"
    IMPORT_CMD="push metrics http://localhost:9090/api/v1/write /tmp/prometheus-mock-data.txt"
  else
    FORMAT="--openmetrics"
    IMPORT_CMD="tsdb create-blocks-from openmetrics /tmp/prometheus-mock-data.txt /prometheus"
  fi

  DATA_FILE=${DATA_FILE:-}
  if [ -z "${DATA_FILE}" ]; then
    DATA_FILE=$(mktemp -t prometheus-mock-data-XXXXX.txt)
    pushd "$(git rev-parse --show-toplevel)" > /dev/null
    poetry run swatchdog prometheus mock-data "$FORMAT" --file="$DATA_FILE" $SWATCHDOG_ARGS
    popd > /dev/null
  fi

  podman cp "$DATA_FILE" "$CONTAINER_ID":/tmp/prometheus-mock-data.txt
  podman exec "$CONTAINER_ID" /bin/promtool $IMPORT_CMD
}

PORT="9090"
TSDB=0
CONTAINER_ID="prometheus-mock"
START_CONTAINER=1

# Default to empty unless already specified in the environment
: "${SWATCHDOG_ARGS:=}"

while getopts ":htp:c:m:f:" o; do
    case "$o" in
        p)
            PORT=${OPTARG}
            ;;
        t)
            TSDB=1
            ;;
        c)
            CONTAINER_ID=${OPTARG}
            START_CONTAINER=0
            ;;
        m)
            SWATCHDOG_ARGS=${OPTARG}
            ;;
        f)
            DATA_FILE=${OPTARG}
            ;;
        h)
            usage
            exit 0
            ;;
        *)
            usage
            exit 0
            ;;
    esac
done
shift $((OPTIND-1))

PROM_FILE=$(mktemp -t prometheus-XXXXX.yml)
# Give the container permission to read the configuration file
chmod 644 "$PROM_FILE"

cat <<'EOF' > "$PROM_FILE"
# Allow 365 days grace on adding out of order records
storage:
  tsdb:
    out_of_order_time_window: 365d

global:
  scrape_interval: 15s # Set the scrape interval to every 15 seconds. Default is every 1 minute.
  evaluation_interval: 15s # Evaluate rules every 15 seconds. The default is every 1 minute.
  # scrape_timeout is set to the global default (10s).

# Alertmanager configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets:
          # - alertmanager:9093

# A scrape configuration containing exactly one endpoint to scrape:
# Here it's Prometheus itself.
scrape_configs:
  - job_name: "prometheus"

    # metrics_path defaults to '/metrics'
    # scheme defaults to 'http'.

    static_configs:
      - targets: ["localhost:9090"]
EOF

# Run this block in a subshell in the background and wait until the prometheus pod starts successfully
{
  if [ $START_CONTAINER -eq 1 ]; then
    sleep 2
    container_ps=$(podman ps -f "name=^$CONTAINER_ID$" --noheading)
    while [[ -z "$container_ps" || "$(podman inspect -f '{{.State.Health.Status}}' "$CONTAINER_ID")" != "healthy" ]]; do
      echo "Waiting for Prometheus to start...";
      sleep 2;
    done
    import
  fi
} &

if [ $START_CONTAINER -eq 1 ]; then
  # Disable the error trap here.  People will generally CTRL-C the container when they are done.  If the
  # podman command actually does fail to run, this is the last command in the script so the error will be right there
  # on the console.  If refactoring changes the order of how the commands are run, another solution will be required.
#  trap - ERR
  podman run --name "$CONTAINER_ID" \
    --rm \
    --health-cmd='/bin/promtool check healthy' \
    --health-startup-cmd='/bin/promtool check ready' \
    -p "$PORT":9090 \
    -v "$PROM_FILE":/etc/prometheus/prometheus.yml:Z \
    quay.io/prometheus/prometheus \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus \
    --web.console.libraries=/usr/share/prometheus/console_libraries \
    --web.console.templates=/usr/share/prometheus/consoles \
    --web.enable-remote-write-receiver \
    --web.enable-admin-api
else
  import
fi
