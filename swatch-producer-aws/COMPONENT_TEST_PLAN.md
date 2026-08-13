# Introduction

The **swatch-producer-aws** module is a service within the Subscription Watch platform that consumes hourly billable-usage aggregates from Kafka, looks up AWS contract context from **swatch-contracts**, and submits usage to AWS Marketplace via `BatchMeterUsage`. It publishes remittance status to `platform.rhsm-subscriptions.billable-usage.status` and increments `swatch_producer_metered_total` for observability.

This document outlines the test plan for swatch-producer-aws.

**Purpose:** To ensure the swatch-producer-aws service is functional, reliable, and meets all defined requirements for AWS usage remittance.

**Scope:**

* Kafka consumption of AWS billable-usage hourly aggregates
* Contracts API lookup (`awsUsageContext`) and error handling
* AWS Marketplace `BatchMeterUsage` submission
* Remittance status emission and metrics

**Assumptions:**

* The swatch-producer-aws service is deployed in a stable and functional environment
* **swatch-contracts** provides accurate `AwsUsageContext` for subscribed orgs (including `customerAwsAccountId` and, when applicable, license-scoped context per SWATCH-5297)
* Kafka is available for billable-usage input and status output topics
* Billable-usage hourly aggregates may carry `licenseId` on the aggregate body (SWATCH-5301)
* AWS Marketplace and contracts APIs are stubbed via Wiremock in component tests

**Constraints:**

* Testing is limited to the functionality of swatch-producer-aws at a component level
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan

# Test Strategy

This test plan focuses on covering test scenarios for component-level tests utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

- Billable-usage aggregates can be published directly to the Kafka `billable-usage-hourly-aggregate` topic
- Contracts `awsUsageContext` and AWS `BatchMeterUsage` can be stubbed via Wiremock (`AwsWiremockService`)
- Outcomes can be verified on the `billable-usage.status` topic and, where relevant, on captured AWS request bodies

# Test Cases

## Customer identification on BatchMeterUsage

Usage records always use `CustomerAWSAccountId` from `AwsUsageContext.customerAwsAccountId`. Covered by component tests in `CustomerAwsAccountIdComponentTest`.

**producer-aws-customer-id-TC001 - Use CustomerAWSAccountId on BatchMeterUsage**

- **Description**: Verify that usage records sent to AWS use `CustomerAWSAccountId` from `AwsUsageContext.customerAwsAccountId`.
- **Setup**:
  - Wiremock returns `awsUsageContext` with distinct `customerId` and `customerAwsAccountId`
  - Kafka topics for billable-usage hourly aggregate and status are available
- **Action**:
  - Produce a valid AWS billable-usage aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request in Wiremock
- **Expected Result**:
  - `UsageRecords[0].CustomerAWSAccountId` matches `customerAwsAccountId` from context
  - `CustomerIdentifier` is absent from the usage record
  - `ProductCode` is still sent on the request

## LicenseArn on BatchMeterUsage (SWATCH-5050 / SWATCH-5303)

**producer-aws-license-TC001 - Omit LicenseArn when feature flag is off**

- **Description**: Verify that with `use-license` disabled (default), usage records do not include `LicenseArn` even if the aggregate carries `licenseId`.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-license` is off
  - Produce an AWS billable-usage aggregate **with** `licenseId` set on the aggregate body
  - Wiremock returns a valid `awsUsageContext`
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request
- **Expected Result**:
  - `UsageRecords[0].LicenseArn` is absent / null
  - `ProductCode` is still present on the request
  - Remittance status is `SUCCEEDED`

**producer-aws-license-TC002 - Set LicenseArn when feature flag is on and licenseId is present**

- **Description**: Verify that with `use-license` enabled and aggregate `licenseId` present, `BatchMeterUsage` includes matching `LicenseArn`.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-license` is enabled
  - Aggregate body includes `licenseId` (e.g. `arn:aws:license-manager:...:license:swatch-test-license`)
  - Wiremock stubs `getAwsUsageContext` successfully (with optional `licenseId` query param per SWATCH-5297 when implemented)
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request and status payload
- **Expected Result**:
  - `UsageRecords[0].LicenseArn` equals the aggregate `licenseId`
  - `ProductCode` is still present
  - Status message / aggregate on `billable-usage.status` carries the same `licenseId`
  - Remittance status is `SUCCEEDED`

**producer-aws-license-TC003 - Omit LicenseArn when flag is on but licenseId is missing**

- **Description**: Verify that with `use-license` enabled but aggregate `licenseId` null/absent, the producer meters without LicenseArn (legacy shape), logs a warning, and still succeeds.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-license` is enabled
  - Aggregate has **no** `licenseId`
  - Wiremock returns a valid `awsUsageContext`
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request
- **Expected Result**:
  - Remittance status is `SUCCEEDED`
  - `UsageRecords[0].LicenseArn` is absent / null
  - `ProductCode` is still present
  - Warning logged about missing `licenseId` while `use-license` is on
  - Status may emit with null `licenseId`

**producer-aws-license-TC004 - LicenseArn combined with CustomerAWSAccountId**

- **Description**: Verify `CustomerAWSAccountId` (always set) and `LicenseArn` (when `use-license` is on) can appear together on the usage record.
- **Setup**:
  - `use-license` enabled
  - Aggregate includes `licenseId`
  - Wiremock `awsUsageContext` includes `customerAwsAccountId`
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request
- **Expected Result**:
  - `UsageRecords[0].CustomerAWSAccountId` matches context
  - `UsageRecords[0].LicenseArn` matches aggregate `licenseId`
  - `CustomerIdentifier` absent
  - `ProductCode` still present

**producer-aws-license-TC005 - Pass licenseId to getAwsUsageContext when looking up context**

- **Description**: verify the producer requests usage context for the specific license used for billing.
- **Setup**:
  - `use-license` enabled
  - Aggregate includes `licenseId`
  - Wiremock can match `getAwsUsageContext` with optional `licenseId` parameter
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Inspect contracts Wiremock recorded request for `awsUsageContext`
- **Expected Result**:
  - Contracts lookup includes the aggregate `licenseId` (when the API contract requires/accepts it)
  - Successful remittance uses that context
  - If no contract matches the licenseId, behavior matches contracts error handling (404 / existing classification - see usage-context section)

**producer-aws-license-TC006 - Status emission includes licenseId on success path**

- **Description**: Verify remittance status messages carry the `licenseId` used for metering after a successful AWS remittance (`emitStatus` success path; remittance correlation per SWATCH-5303 / SWATCH-5335).
- **Setup**:
  - `use-license` enabled
  - Aggregate includes `licenseId`
  - Wiremock stubs successful `getAwsUsageContext` and successful AWS `BatchMeterUsage`
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Read `billable-usage.status` payload
- **Expected Result**:
  - Status is successful and the status aggregate includes the metering `licenseId`

**producer-aws-license-TC007 - Status emission includes licenseId on failure path**

- **Description**: Verify remittance status messages still carry `licenseId` when remittance fails after `licenseId` was known (`emitStatus` failure path; remittance correlation per SWATCH-5303 / SWATCH-5335).
- **Setup**:
  - `use-license` enabled
  - Aggregate includes `licenseId`
  - Wiremock stubs a contracts or AWS failure that still emits status (e.g. contracts `awsUsageContext` 404)
- **Action**:
  - Produce the aggregate to Kafka
- **Verification**:
  - Read `billable-usage.status` payload
- **Expected Result**:
  - Status is `FAILED` with the applicable error code
  - Status aggregate still includes `licenseId`

## AWS usage context error handling

Contracts `awsUsageContext` lookup failures and remittance error classification. Covered by component tests in `AwsUsageContextComponentTest`.

**producer-aws-usage-context-TC001 - Classify recently terminated subscription**

- **Description**: Verify that when swatch-contracts returns 404 with `CONTRACTS1005`, remittance status is `SUBSCRIPTION_TERMINATED` (not `SUBSCRIPTION_NOT_FOUND`).
- **Setup**:
  - Wiremock returns `awsUsageContext` 404 with an `Error` body containing `CONTRACTS1005`
  - Kafka topics for billable-usage hourly aggregate and status are available
- **Action**:
  - Produce a valid AWS billable-usage aggregate to Kafka
- **Verification**:
  - Wait for `FAILED` on `billable-usage.status` with error code `SUBSCRIPTION_TERMINATED`
  - No `BatchMeterUsage` call is sent to AWS
- **Expected Result**:
  - Remittance status is `FAILED` with `SUBSCRIPTION_TERMINATED`
  - AWS Marketplace is not called
