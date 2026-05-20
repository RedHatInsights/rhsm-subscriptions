# Stage validation: vendor-neutral observability env vars (SWATCH-4882)

After deploying the `LOGGING_*` / `TRACES_*` rename to **rhsm-stage**, confirm behavior is unchanged: application logs still reach Splunk/SumoLogic and traces still reach Splunk Observability Cloud (SignalFx).

## Preconditions

- rhsm-subscriptions and app-interface changes for SWATCH-4882 are deployed to stage (Clowder picks up new parameter names).
- You can query **Splunk** (HEC / platform logs) and **Splunk Observability Cloud** (APM/traces) for `rhsm-stage`.

## 1. Logging (HEC → Splunk / SumoLogic)

Pick a service that ships logs in stage (e.g. `swatch-utilization`, `swatch-contracts`).

### Pod env check

```bash
NS=rhsm-stage
POD=$(oc get pod -n "$NS" -l app=swatch-utilization -o jsonpath='{.items[0].metadata.name}')
oc exec -n "$NS" "$POD" -c service -- printenv | grep -E '^LOGGING_|^ENABLE_LOGGING'
```

Expect (examples):

- `ENABLE_LOGGING_HEC=true`
- `LOGGING_HEC_URL=https://splunk-hec.redhat.com:8088`
- `LOGGING_SOURCE=swatch-utilization`

### Splunk search (recent logs)

Use your usual stage Splunk index/sourcetype. Example (adjust index/sourcetype for your tenant):

```spl
index=* sourcetype=quarkus_service source=swatch-utilization earliest=-15m
| stats count by host
```

Confirm new log lines appear after deploy and fields (`source`, `sourcetype`) match pre-change behavior.

### SumoLogic (if routed via Cribl)

If stage logs are also searchable in SumoLogic, run an equivalent query on `swatch-utilization` (or the service under test) for the last 15 minutes and confirm volume is non-zero.

## 2. Traces (OTLP sidecar → SignalFx / Splunk Observability)

Most SWATCH deployments run with `OTEL_DISABLED=true` in stage; tracing validation applies where the **otel-collector sidecar** is enabled (e.g. `swatch-tally`, `swatch-api` nginx proxy when configured).

ClowdApp templates set **`TRACES_*`** env vars for operators and Clowder parameters. The `quay.io/signalfx/splunk-otel-collector` image still reads **`SPLUNK_ACCESS_TOKEN`**, **`SPLUNK_CONFIG`**, and **`SPLUNK_MEMORY_TOTAL_MIB`**, so the sidecar also defines those names as passthroughs (`value: $(TRACES_…)`). Stage validation must confirm the sidecar starts and exports traces, not only that `TRACES_*` is present.

### Pod env check (sidecar)

```bash
NS=rhsm-stage
# Example: swatch-tally worker with sidecar enabled
POD=$(oc get pod -n "$NS" -l app=swatch-tally -o jsonpath='{.items[0].metadata.name}')
oc exec -n "$NS" "$POD" -c otel-collector -- printenv | grep -E '^TRACES_|^OTEL_'
```

Expect:

- `TRACES_ACCESS_TOKEN` set (value redacted)
- `TRACES_CONFIG=/etc/otelcol/relay` (or equivalent)
- Template parameters `OTEL_TRACES_TOKEN_SECRET_NAME` / `OTEL_TRACES_TOKEN_SECRET_KEY` resolve to the existing `signalfx` secret.

### Generate trace traffic

Use an internal RPC that returns `traceresponse` (see [observability.md](./observability.md)), e.g. swatch-system-conduit `syncOrg` or swatch-tally hourly tally for a test org.

Note the trace ID from the `traceresponse` header.

### Splunk Observability Cloud (SignalFx)

In **APM / Traces**, search for the service name (e.g. `swatch-tally`) in `rhsm-stage` and filter by the trace ID from the step above. Confirm spans appear within a few minutes of the request.

### Daemonset collector (optional)

The cluster-level `splunk-otel-collector` deployment (app-interface `deploy.yml`) uses `TRACES_TOKEN_SECRET_*` parameters after SWATCH-4882; confirm that deployment is healthy:

```bash
oc get deploy splunk-otel-collector -n rhsm-stage
oc logs -n rhsm-stage deploy/splunk-otel-collector --tail=50
```

## 3. Rollback signal

If logs or traces stop after deploy:

1. Compare Clowder-deployed env on the pod (`oc set env` / `printenv`) with pre-change names (`ENABLE_SPLUNK_HEC`, `SPLUNK_HEC_*`, `SPLUNK_OBSERVABILITY_ACCESS_TOKEN`).
2. Confirm app-interface `deploy-clowder.yml` parameters use `ENABLE_LOGGING_HEC` / `LOGGING_HEC_*` for the affected component.
3. For sidecar trace export, verify `quay.io/signalfx/splunk-otel-collector:0.140.0` accepts `TRACES_*` env vars or coordinate an image/config update with the platform team.

## Sign-off

| Check | Owner | Date | Pass? |
|-------|-------|------|-------|
| Stage logs (Splunk) | | | |
| Stage logs (SumoLogic, if applicable) | | | |
| Stage traces (SignalFx) | | | |
| No unexpected pod crash loops on otel sidecar | | | |

Link this file from [SWATCH-4882](https://redhat.atlassian.net/browse/SWATCH-4882) when the MR/PR is ready for stage verification.
