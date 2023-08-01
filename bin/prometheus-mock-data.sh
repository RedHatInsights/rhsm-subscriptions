#!/bin/sh
COMMAND=$1

run_container() {
  SCRIPT_DIR=$(realpath $(dirname "$0"))
  podman run --name prometheus-mock \
    -e CLUSTER_ID \
    -e BILLING_PROVIDER \
    -e METRICS \
    -e PRODUCT \
    -e MARKETPLACE_ACCOUNT \
    -e ACCOUNT \
    -e ORG_ID \
    --rm \
    -p 9090:9090 \
    -v $SCRIPT_DIR/prometheus-mock-data.sh:/entrypoint.sh:Z \
    --entrypoint=/entrypoint.sh \
    quay.io/prometheus/prometheus entrypoint
}

entrypoint() {
  CLUSTER_ID=${CLUSTER_ID:-test01}
  BILLING_PROVIDER=${BILLING_PROVIDER:-aws}
  METRICS="${METRICS:-kafka_id:kafka_broker_quota_totalstorageusedbytes:max_over_time1h_gibibyte_months}"
  PRODUCT=${PRODUCT:-rhosak}
  MARKETPLACE_ACCOUNT=${MARKETPLACE_ACCOUNT:-mktp-123}
  ACCOUNT=${ACCOUNT:-account123}
  ORG_ID=${ORG_ID:-org123}

  FILE=$(mktemp)
  NOW=$(date +%s)
  # start at 24 hours ago
  TIME=$(($NOW - 86400))

  echo "Generating mock data for _id=$CLUSTER_ID,product=$PRODUCT,metrics=$METRICS"

  cat <<EOF > $FILE
# HELP ocm_subscription placeholder
# TYPE ocm_subscription counter
EOF

  for METRIC in $METRICS; do
  cat <<EOF >> $FILE
# HELP $METRIC placeholder
# TYPE $METRIC counter
EOF
  done

  while [ $TIME -lt $NOW ]; do
    TIME=$(($TIME + 300))
    cat <<EOF >> $FILE
ocm_subscription{_id="$CLUSTER_ID",billing_model="marketplace",ebs_account="$ACCOUNT",external_organization="$ORG_ID",support="Premium",billing_provider="$BILLING_PROVIDER",billing_marketplace_account="$MARKETPLACE_ACCOUNT",product="$PRODUCT"} 1.0 $TIME
EOF
    for METRIC in $METRICS; do
      VALUE=$(($RANDOM % 100))
      cat <<EOF >> $FILE
$METRIC{_id="$CLUSTER_ID"} $VALUE $TIME
EOF
    done
  done

  echo "# EOF" >> $FILE

  echo "Importing mock data"
  promtool tsdb create-blocks-from openmetrics $FILE /prometheus
  echo "Running prometheus"
  # prometheus command copied from the container entrypoint
  exec prometheus \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus \
    --web.console.libraries=/usr/share/prometheus/console_libraries \
    --web.console.templates=/usr/share/prometheus/consoles
}

if [ "$COMMAND" = "entrypoint" ] ; then
  entrypoint
else
  run_container
fi
