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
* **swatch-contracts** provides accurate `AwsUsageContext` for subscribed orgs
* Kafka is available for billable-usage input and status output topics
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
- Unleash toggles can be exercised through the component-test harness (`AwsUnleashService`) when feature-flag behavior is under test

# Test Cases

## Customer identification on BatchMeterUsage

When the feature flag is enabled, usage records may use `CustomerAWSAccountId` from `AwsUsageContext.customerAwsAccountId` instead of `CustomerIdentifier`. Covered by component tests in `CustomerAwsAccountIdComponentTest`.

**Feature flag covered:** `swatch.swatch-producer-aws.use-customer-aws-account-id` (default off)

**producer-aws-customer-id-TC001 - Use CustomerIdentifier when feature flag is off**

- **Description**: Verify that with the toggle disabled (default), usage records sent to AWS use `CustomerIdentifier` from `AwsUsageContext.customerId`.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-customer-aws-account-id` is off
  - Wiremock returns `awsUsageContext` with distinct `customerId` and `customerAwsAccountId`
  - Kafka topics for billable-usage hourly aggregate and status are available
- **Action**:
  - Produce a valid AWS billable-usage aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request in Wiremock
- **Expected Result**:
  - `UsageRecords[0].CustomerIdentifier` equals `customerId`
  - `CustomerAWSAccountId` is absent from the usage record

**producer-aws-customer-id-TC002 - Use CustomerAWSAccountId when feature flag is on**

- **Description**: Verify that with the toggle enabled and `customerAwsAccountId` present, usage records use `CustomerAWSAccountId`.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-customer-aws-account-id` is enabled
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

**producer-aws-customer-id-TC003 - Fall back to CustomerIdentifier when account id is missing**

- **Description**: Verify that with the toggle enabled but `customerAwsAccountId` absent from context, the producer logs a warning and falls back to `CustomerIdentifier`.
- **Setup**:
  - Unleash toggle `swatch.swatch-producer-aws.use-customer-aws-account-id` is enabled
  - Wiremock returns `awsUsageContext` without `customerAwsAccountId`
  - Kafka topics for billable-usage hourly aggregate and status are available
- **Action**:
  - Produce a valid AWS billable-usage aggregate to Kafka
- **Verification**:
  - Wait for `SUCCEEDED` on `billable-usage.status`
  - Inspect captured `BatchMeterUsage` request in Wiremock
- **Expected Result**:
  - Remittance status is `SUCCEEDED`
  - `UsageRecords[0].CustomerIdentifier` equals `customerId`
  - Warning logged about missing `customerAwsAccountId`

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
