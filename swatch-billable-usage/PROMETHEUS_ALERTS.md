# Prometheus Alerting Configuration for SWATCH-4013

This document provides Prometheus alert rules for monitoring billable usage granularity filtering (SWATCH-4013). These alerts ensure that only HOURLY snapshots are being processed and notify the team if DAILY snapshots appear in production.

## Overview

The billable usage service now exposes a metric to track filtered snapshots by granularity:

- **Metric Name**: `swatch_billable_usage_snapshots_filtered_total`
- **Type**: Counter
- **Labels**:
  - `granularity`: The granularity that was filtered (DAILY, YEARLY, MONTHLY, etc.)
  - `product`: The product ID (rosa, rhel, etc.)
  - `reason`: Why it was filtered (`non_hourly`)

## Alert Rules

Add the following alert rules to your Prometheus configuration:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: swatch-billable-usage-granularity-alerts
  namespace: subscription-watch
spec:
  groups:
    - name: billable-usage-granularity
      interval: 5m
      rules:
        # CRITICAL: DAILY snapshots detected in billable usage pipeline
        - alert: DailySnapshotsInBillableUsagePipeline
          expr: |
            increase(swatch_billable_usage_snapshots_filtered_total{granularity="DAILY"}[15m]) > 0
          for: 5m
          labels:
            severity: critical
            component: billable-usage
            swatch_jira: SWATCH-4013
          annotations:
            summary: "DAILY snapshots detected in billable usage service"
            description: |
              DAILY granularity snapshots have been filtered in the billable usage service for product {{ $labels.product }}.

              This is CRITICAL because:
              - Only HOURLY snapshots should be sent to billable usage (SWATCH-4013)
              - DAILY snapshots indicate an upstream issue in the tally service
              - Processing DAILY snapshots would result in INCORRECT billing data

              Filtered count in last 15 minutes: {{ $value }}
              Product: {{ $labels.product }}

              Action Required:
              1. Check tally service configuration - why are DAILY snapshots being sent?
              2. Verify no DAILY snapshots reached the billing providers (AWS/Azure)
              3. Review logs for tallyId to trace the source
              4. Open incident ticket and notify #swatch-team

        # WARNING: High rate of non-hourly snapshot filtering
        - alert: HighRateOfNonHourlySnapshotFiltering
          expr: |
            rate(swatch_billable_usage_snapshots_filtered_total{reason="non_hourly"}[30m]) > 10
          for: 15m
          labels:
            severity: warning
            component: billable-usage
            swatch_jira: SWATCH-4013
          annotations:
            summary: "High rate of non-hourly snapshots being filtered"
            description: |
              The billable usage service is filtering a high rate of non-hourly snapshots.

              Rate over last 30 minutes: {{ $value }} snapshots/second
              Granularity: {{ $labels.granularity }}
              Product: {{ $labels.product }}

              This may indicate:
              - Misconfiguration in the tally service
              - Unexpected granularity snapshots being produced

              Action Required:
              1. Review tally service logs
              2. Check if this is expected (e.g., during migration or testing)
              3. If unexpected, investigate why non-hourly snapshots are being generated

        # INFO: Monitoring baseline for filtered snapshots
        - alert: NonHourlySnapshotsFiltered
          expr: |
            increase(swatch_billable_usage_snapshots_filtered_total{reason="non_hourly"}[1h]) > 0
          labels:
            severity: info
            component: billable-usage
            swatch_jira: SWATCH-4013
          annotations:
            summary: "Non-hourly snapshots are being filtered (informational)"
            description: |
              Billable usage service filtered {{ $value }} non-hourly snapshots in the last hour.

              Granularity: {{ $labels.granularity }}
              Product: {{ $labels.product }}

              This is expected behavior if:
              - Granularity is YEARLY, MONTHLY, QUARTERLY, WEEKLY (normal filtering)

              This is NOT expected if:
              - Granularity is DAILY (should never appear - see DailySnapshotsInBillableUsagePipeline alert)
```

## Alert Severity Levels

### Critical: `DailySnapshotsInBillableUsagePipeline`
- **Trigger**: Any DAILY snapshots detected in last 15 minutes
- **Impact**: HIGH - Indicates potential data integrity issue
- **Action**: Immediate investigation required
- **Notification**: Page on-call engineer

### Warning: `HighRateOfNonHourlySnapshotFiltering`
- **Trigger**: > 10 non-hourly snapshots/second for 15 minutes
- **Impact**: MEDIUM - May indicate configuration issue
- **Action**: Investigate during business hours
- **Notification**: Slack notification to #swatch-alerts

### Info: `NonHourlySnapshotsFiltered`
- **Trigger**: Any non-hourly snapshots filtered in last hour
- **Impact**: LOW - Informational/baseline monitoring
- **Action**: No action required unless DAILY appears
- **Notification**: Metrics dashboard only

## Grafana Dashboard Queries

Add these queries to your Grafana dashboard for visualization:

### Panel 1: Filtered Snapshots by Granularity
```promql
sum by (granularity) (
  rate(swatch_billable_usage_snapshots_filtered_total[5m])
)
```

### Panel 2: DAILY Snapshots Over Time (Should be zero!)
```promql
increase(swatch_billable_usage_snapshots_filtered_total{granularity="DAILY"}[5m])
```

### Panel 3: Filtered Snapshots by Product
```promql
sum by (product, granularity) (
  increase(swatch_billable_usage_snapshots_filtered_total[1h])
)
```

### Panel 4: Filtering Rate (snapshots/sec)
```promql
sum(rate(swatch_billable_usage_snapshots_filtered_total[5m]))
```

## Testing the Alerts

### Manual Test (Staging Only)

To verify the alerts are working in staging:

1. **Send a test DAILY snapshot** (via manual Kafka message or test harness)
2. **Verify the metric increments**:
   ```bash
   curl -s http://swatch-billable-usage:8080/q/metrics | grep swatch_billable_usage_snapshots_filtered_total
   ```
3. **Check Prometheus**:
   ```promql
   swatch_billable_usage_snapshots_filtered_total{granularity="DAILY"}
   ```
4. **Verify alert fires** in Prometheus UI (Alerts page)
5. **Verify notification** reaches Slack/#swatch-alerts

### Expected Behavior

- **Normal operation**: Only YEARLY, MONTHLY, QUARTERLY, WEEKLY snapshots are filtered
- **DAILY appears**: Alert fires immediately → investigate tally service
- **No filtering**: May indicate all snapshots are HOURLY (expected) OR filtering is broken (unexpected - verify with logs)

## Runbook: Responding to DAILY Snapshot Alert

When `DailySnapshotsInBillableUsagePipeline` alert fires:

### 1. Assess the Situation
```bash
# Check how many DAILY snapshots were filtered
increase(swatch_billable_usage_snapshots_filtered_total{granularity="DAILY"}[1h])

# Check affected products
swatch_billable_usage_snapshots_filtered_total{granularity="DAILY"}
```

### 2. Review Logs
```bash
# Search for DAILY snapshot filtering (includes tallyId for tracing)
oc logs -l app=swatch-billable-usage --tail=1000 | grep "Filtered DAILY snapshot"

# Example output:
# Filtered DAILY snapshot - productId=rosa, snapshotDate=2025-11-24T12:00:00Z, tallyId=abc-123-def
```

### 3. Trace the Source
Using the `tallyId` from logs:
```bash
# Find the original tally summary that produced this snapshot
oc logs -l app=swatch-tally --tail=10000 | grep "abc-123-def"
```

### 4. Verify No Downstream Impact
```bash
# Check if any DAILY data reached the billing producers
oc logs -l app=swatch-producer-aws --tail=1000 | grep "abc-123-def"
oc logs -l app=swatch-producer-azure --tail=1000 | grep "abc-123-def"
```

### 5. Remediation
- **If tally service misconfigured**: Fix configuration, restart tally service
- **If data sent to AWS/Azure**: Contact billing team to investigate potential impact
- **If recurring**: Create incident ticket and schedule root cause analysis

## Maintenance

- **Review alert thresholds quarterly** based on observed patterns
- **Update runbooks** as investigation procedures evolve
- **Archive alert history** for trend analysis

## Related Documentation

- **SWATCH-4013**: Main JIRA ticket for hourly-only processing requirement
- **SWATCH-3790**: Parent ticket - system sending both DAILY and HOURLY summaries
- **Test Plan**: See `BILLABLE_USAGE_HOURLY_ONLY_TEST_PLAN.md` in this directory
- **Code**: `BillableUsageMapper.java:108` (metric tracking implementation)