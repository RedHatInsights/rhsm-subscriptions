# swatch-producer-gcp POC

A small Quarkus producer following the AWS/Azure pattern: consume a
`BillableUsageAggregate`, resolve Google billing context, use Google’s
`com.google.cloud:google-cloud-service-control` Java SDK to call Service Control
`check` then `report`, and publish the aggregate's submission status. A local
HTTP endpoint invokes the same submission service and returns the generated
requests for inspection; HTTP calls do not publish Kafka statuses.

## Run locally

From the repository root, using Java 25 and Maven:

```sh
mvn -pl swatch-producer-gcp -am install -DskipTests
mvn -pl swatch-producer-gcp quarkus:dev
```

In another terminal, from the repository root:

```sh
curl --fail-with-body -sS http://localhost:8015/api/swatch-producer-gcp/poc/usage \
  -H 'Content-Type: application/json' \
  --data-binary @swatch-producer-gcp/examples/usage.json
```

The SDK constructs typed v1 `Operation`, `CheckRequest` and `ReportRequest`
objects. The response has `status: DRY_RUN`, the check/report URLs, and both request
bodies. The example produces `consumerId: project_number:123456789012`, metric
`example-product/instance_hours`, and quantity `150`. No Google credentials or
Kafka broker are needed. The default messaging connectors are in memory.
Dry runs do not publish a successful billing status.

## Placeholder handoff from upstream

`GcpUsageContextLookup` uses exact matches from `application.properties` as a
stand-in for a future `swatch-contracts` endpoint. Replace the customer and meter
entries with real values when ready:

- Customer context maps `(orgId, billingAccountId, productId)` to the entitlement's
  **`usageReportingId`**. The upstream subscription integration obtains this
  identifier; the producer passes it unchanged as **`consumerId`**. The sample's
  SWATCH `billingAccountId` is a lookup key, not automatically Google's
  `usageReportingId`. An unmatched customer is rejected.
- Product configuration maps `(productId, metricId)` to the Marketplace
  **service domain** and approved **metric name**. Real values come from Producer
  Portal. The example assumes an INT64 delta metric; values must already be in
  its billing units. Fractions, negative values and INT64 overflow are rejected.
- The producer uses **`aggregateId`** as **`operationId`**, preserving it across
  check, report and retries of the same immutable aggregate. Preserve the ID and
  payload when replaying; changing the quantity under the same ID is unsupported.

## Required usage interval

**Each aggregate must contain usage from exactly one actual UTC usage hour.**
This is a POC requirement: all entries in `snapshotDates` must fall within the
same UTC hour, and `totalValue` must represent usage for that hour.

The producer sets an explicit interval on the Service Control `Operation`:

- `startTime` is the UTC hour containing `snapshotDates`, truncated to the hour.
- `endTime` is `startTime + 1 hour`.
- The metric value inherits this interval. The check and report use the same
  operation and interval.

For example, a snapshot at `2026-11-04T12:35:00Z` produces `startTime` of
`2026-11-04T12:00:00Z` and `endTime` of `2026-11-04T13:00:00Z`.

Missing or empty `snapshotDates`, null entries, and aggregates spanning multiple
UTC usage hours are rejected. An aggregate's `totalValue` cannot be reliably
split back into its original hours. `windowTimestamp` represents the processing
window and is not used to determine the usage interval.

Production integration must preserve actual usage hours upstream, for example by
grouping GCP usage by its actual billing hour before producing these aggregates.

## Try Kafka or live Service Control

Set `QUARKUS_PROFILE=kafka` to use the same input and status topics as the other
producers, with a separate consumer group. `KAFKA_BOOTSTRAP_SERVERS` defaults to
`localhost:9092`. Google calls still default to dry run. The consumer ignores
other billing providers. Live successful submissions emit `SUCCEEDED` with the
original aggregate/remittance IDs; submission errors emit `FAILED` with `UNKNOWN`.

For a manual live experiment, configure real mappings, set `GCP_DRY_RUN=false`,
and configure Application Default Credentials (ADC), for example through
`GOOGLE_APPLICATION_CREDENTIALS` pointing at a service-account or workload
identity configuration. The SDK handles authentication and token refresh.
The credential must be authorized for
`servicemanagement.services.check` and `servicemanagement.services.report` on the
service (typically `roles/servicemanagement.serviceController`). The SDK uses
its HTTP/JSON transport against `GCP_ENDPOINT`, which defaults to
`https://servicecontrol.googleapis.com`. For local stub testing only,
`GCP_EMULATOR_ENDPOINT=http://127.0.0.1:PORT` bypasses ADC; emulator endpoints
are restricted to loopback hosts.
Use Google's Test Billing Account and current usage timestamps for live testing.
The included dated fixture is for preview/stub testing.

## POC boundaries

This is a local experiment, with its HTTP interface bound to loopback. Production
work includes contracts lookup with subscription/plan effective dates, registry
configuration and unit conversion, workload identity setup,
authentication for administrative HTTP access, deployment configuration,
provider-specific errors/retries, and durable submission/replay tracking.
The POC does not enforce reporting deadlines or handle usage corrections.
Google requires reporting within one hour of usage; SWATCH's processing-hour
aggregation and delayed usage paths need review against that requirement.
An accepted Service Control response indicates submission acceptance, not invoice
reconciliation.

## Verify

```sh
mvn -pl swatch-producer-gcp -am test -Dtest='Gcp*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

Tests cover request mapping, invalid contexts/quantities/usage intervals,
dry-run behavior, the check-before-report HTTP exchange against a local stub,
HTTP-200 error arrays, HTTP failures, and correlated billing statuses.

## Google references

- [Java SDK](https://docs.cloud.google.com/java/docs/reference/google-cloud-service-control/latest/com.google.api.servicecontrol.v1.ServiceControllerClient)
- [SDK configuration and ADC](https://docs.cloud.google.com/java/docs/reference/google-cloud-service-control/latest/com.google.api.servicecontrol.v1.ServiceControllerSettings)
- [Usage reporting and identifiers](https://docs.cloud.google.com/marketplace/docs/partners/integrated-saas/configure-usage-reports)
- [Service Control check](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/services/check)
- [Service Control report and per-operation errors](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/services/report)
- [Operation IDs](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/Operation)
- [Metric value types](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/MetricValueSet)
- [Reporting deadlines](https://docs.cloud.google.com/marketplace/docs/partners/integrated-saas/best-practices-reporting)
