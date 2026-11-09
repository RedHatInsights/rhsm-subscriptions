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

### Required ITPEG handoff

**ITPEG (`ibm-entitlement-gateway`) must capture and persist the GCP entitlement's
`usageReportingId`, then expose it downstream alongside `gcpEntitlementId` and
the Red Hat account/subscription mapping.** Its current GCP entitlement mapping
does not retain this field.

Carry this value through `swatch-contracts` to the GCP producer, which passes it
unchanged as Service Control's `consumerId` when reporting billable usage.
Keep it separate from the procurement account ID (`gcpAccountId`), Cloud Billing
account ID (`gcpBillingAccountId`), and entitlement ID (`gcpEntitlementId`);
those identifiers do not replace `usageReportingId`.

An observed ITPEG response contains `partnerIdentities.gcpAccountId` and
`partnerIdentities.gcpEntitlementId`, but neither `usageReportingId` nor
`gcpBillingAccountId`. Other fields provide subscription context:

- `rhAccountId` identifies the Red Hat account; upstream must resolve its SWATCH
  organization mapping.
- `rhEntitlements[].subscriptionNumber` identifies the Red Hat subscription.
- `purchase.vendorProductCode` identifies the Marketplace product, and
  `purchase.contracts[].planId` identifies its purchased plan. Confirm the Service
  Control service domain and billing metric names in Producer Portal.
- `entitlementDates.startDate` and `endDate` provide effective dates for resolving
  the entitlement associated with the actual usage interval.

The response can contain multiple entitlements with the same GCP account and
product, including overlapping dates for different plans. Production lookup must
preserve entitlement/subscription identity, plan, and effective dates, associate
usage with the correct entitlement, and reject ambiguous matches. The POC's
account/product placeholder lookup does not yet implement this resolution.

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

### Credentials: follow ITPEG's mounted-file approach

For live calls, provision a Google service-account JSON key through the deployment's
secret management and mount it at `/opt/app/config/gcp-service-account.json`,
following ITPEG's approach. Override the filesystem path using
`GCP_CREDENTIALS_FILE` (`gcp.credentials-file`) when needed. Use a filesystem path,
not a `file:` URI. Credential values belong in the mounted secret, not in the
repository or application properties.

The producer explicitly loads the file with `ServiceAccountCredentials.fromStream()`,
scopes it to `https://www.googleapis.com/auth/cloud-platform`, and supplies it to
the SDK through `FixedCredentialsProvider`. The SDK obtains and refreshes OAuth
access tokens. This uses Google's service-account-specific loader for the same
credential-file model as ITPEG; it does not use ADC discovery or
`GOOGLE_APPLICATION_CREDENTIALS`.
The file is loaded when the SDK client is first created; restart the service
after rotating the mounted key so it loads the replacement credentials.

Configure real customer/metric mappings, mount the file, and set `GCP_DRY_RUN=false`
for a manual live experiment. For example, if the file is mounted elsewhere:

```sh
export GCP_CREDENTIALS_FILE=/run/secrets/gcp-service-account.json
export GCP_DRY_RUN=false
mvn -pl swatch-producer-gcp quarkus:dev
```

The service account must be authorized for
`servicemanagement.services.check` and `servicemanagement.services.report` on the
service (typically `roles/servicemanagement.serviceController`). The SDK uses
its HTTP/JSON transport against `GCP_ENDPOINT`, which defaults to
`https://servicecontrol.googleapis.com`. For local stub testing only,
`GCP_EMULATOR_ENDPOINT=http://127.0.0.1:PORT` bypasses credential loading; emulator
endpoints are restricted to loopback hosts. Dry runs also do not read the file.
Use Google's Test Billing Account and current usage timestamps for live testing.
The included dated fixture is for preview/stub testing.

## POC boundaries

This is a local experiment, with its HTTP interface bound to loopback. Production
work includes contracts lookup with subscription/plan effective dates, registry
configuration and unit conversion, mounted credential provisioning and rotation,
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
dry-run behavior, service-account file loading and authenticated requests against
local token/API stubs, the check-before-report HTTP exchange against a local stub,
HTTP-200 error arrays, HTTP failures, and correlated billing statuses.

## Google references

- [Java SDK](https://docs.cloud.google.com/java/docs/reference/google-cloud-service-control/latest/com.google.api.servicecontrol.v1.ServiceControllerClient)
- [SDK configuration](https://docs.cloud.google.com/java/docs/reference/google-cloud-service-control/latest/com.google.api.servicecontrol.v1.ServiceControllerSettings)
- [Service-account credential loading](https://docs.cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.ServiceAccountCredentials)
- [Usage reporting and identifiers](https://docs.cloud.google.com/marketplace/docs/partners/integrated-saas/configure-usage-reports)
- [Service Control check](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/services/check)
- [Service Control report and per-operation errors](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/services/report)
- [Operation IDs](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/Operation)
- [Metric value types](https://docs.cloud.google.com/service-infrastructure/docs/service-control/reference/rest/v1/MetricValueSet)
- [Reporting deadlines](https://docs.cloud.google.com/marketplace/docs/partners/integrated-saas/best-practices-reporting)
