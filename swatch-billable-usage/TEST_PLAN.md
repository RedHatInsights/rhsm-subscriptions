# Introduction

The **swatch-billable-usage** service is a core billing component within the Subscription Watch (SWATCH) platform. It consumes tally summaries, applies contract coverage and billing factors, maintains a remittance ledger to prevent double-billing, emits billable usage events to Kafka, aggregates usage into hourly windows, and processes marketplace status feedback.

This document defines the **component-level test plan** for `swatch-billable-usage`.

**Purpose:** Ensure `swatch-billable-usage` is functionally correct, reliable, and meets billing requirements at the component boundary, independently of full end-to-end marketplace submission.

**Scope:**

- Tally summary ingestion and PAYG eligibility filtering
- Billable usage calculation (contract coverage, billing factor, remittance delta)
- Contract coverage integration with `swatch-contracts` (mocked in component tests)
- Remittance persistence and lifecycle (`billable_usage_remittance` table)
- Kafka message production and consumption (`tally`, `billable-usage`, `billable-usage-hourly-aggregate`, `billable-usage.status`)
- Kafka Streams hourly aggregation
- Internal Admin API operations (query, reset, flush, delete, purge, reconcile)
- Remittance purge task processing

**Out of scope:**

- Unit tests (covered in service unit test suites; not tracked in this plan)
- End-to-end marketplace API submission (covered by `swatch-producer-aws`, `swatch-producer-azure`, and IQE integration tests)
- `swatch-tally` tally computation logic
- `swatch-contracts` contract creation and sync logic (covered by `swatch-contracts/TEST_PLAN.md`)
- Stage/prod long-run heartbeat tests
- Performance, load, and chaos testing

**Assumptions:**

- `swatch-billable-usage` is deployed with access to the shared `rhsm-subscriptions` PostgreSQL database
- Kafka topics are available and configured for the deployment environment
- `swatch-contracts` REST API is mockable in component tests
- Product configuration (`swatch-product-configuration`) is stable for reference products used in tests

**Constraints:**

- This plan documents component-level test cases only
- Scenarios with full unit test coverage are excluded; IQE and partial-coverage scenarios are retained
- Component tests validate the service in isolation with mocked external dependencies
- Tests must be runnable locally and in ephemeral OpenShift environments

---

# Test Strategy

**Test approach:**

- **Risk-based prioritization** — focus on billing calculation correctness, double-billing prevention, and contract coverage handling
- **Automated component tests** with mocked external dependencies and message injection
- **Verification via multiple channels** — Kafka topic assertions, internal REST API queries, and direct database inspection where needed

# Test Cases

## Tally Summary Ingestion

**billable-usage-tally-ingestion-TC001 - Process a valid hourly PAYG tally summary for Azure**

- **Description:** Verify Azure billing provider tally snapshots are processed identically to AWS.  
- **Setup:**  
  - Mock contracts API: contract coverage stub for Azure product  
  - Prepare `TallySummary` with `billing_provider=azure`
- **Action:**  
  - Publish tally summary to Kafka topic
- **Verification:**  
  - Consume from `billable-usage` topic  
  - Query remittance by tally ID
- **Expected Result:**  
  - Billable usage emitted with `billing_provider=azure`  
  - Remittance record created

**billable-usage-tally-ingestion-TC002 - Ignore TOTAL hardware measurement duplicates**

- **Description:** Verify TOTAL hardware measurement types are filtered to prevent duplicate billing.  
- **Setup:**  
  - Prepare tally snapshot with both regular and TOTAL hardware measurement types
- **Action:**  
  - Publish to tally topic
- **Verification:**  
  - Only non-TOTAL measurements produce billable usage
- **Expected Result:**  
  - One billable usage per non-TOTAL measurement

**billable-usage-tally-ingestion-TC003 - Map one tally summary to multiple billable usages**

- **Description:** Verify a single tally summary with multiple metrics produces multiple billable usage records.  
- **Setup:**  
  - Prepare ROSA tally with both `Cores` and `Instance-hours` measurements
- **Action:**  
  - Publish to tally topic
- **Verification:**  
  - Two messages on `billable-usage` topic  
  - Two remittance records (one per tally snapshot/metric)
- **Expected Result:**  
  - Each metric billed independently with correct billing factor

**billable-usage-tally-ingestion-TC004 - Reject invalid snapshot date without service crash**

- **Description:** Verify malformed `snapshot_date` values do not crash the consumer; subsequent valid messages are processed.  
- **Setup:**  
  - Publish tally with invalid `snapshot_date` (e.g. `"testerday"`)  
  - Then publish valid tally summaries
- **Action:**  
  - Publish invalid then valid messages
- **Verification:**  
  - Service pod does not restart  
  - Valid tallies produce remittances
- **Expected Result:**  
  - Invalid message ignored; valid messages processed normally

---

## Billable Usage Calculation

**billable-usage-billing-calculation-TC001 - No new remittance when current_total decreases**

- **Description:** Verify a lower `current_total` than already remitted does not create a new remittance.  
- **Setup:**  
  - Existing remittance with `remitted_pending_value` = 10  
  - New tally with `current_total` = 6
- **Action:**  
  - Publish new tally summary
- **Verification:**  
  - Only one remittance record exists  
  - Original remittance unchanged
- **Expected Result:**  
  - No new remittance created

**billable-usage-billing-calculation-TC002 - Exclude failed remittances from total remitted calculation**

- **Description:** Verify failed remittances are not counted when computing already-remitted usage.  
- **Setup:**  
  - First remittance: value 10, status `failed`  
  - Second tally: `current_total` = 6
- **Action:**  
  - Publish second tally after failed first remittance
- **Verification:**  
  - New remittance created with value 6  
  - Two remittance rows total (one failed, one pending)
- **Expected Result:**  
  - Failed remittance excluded from already-remitted total

**billable-usage-billing-calculation-TC003 - Update snapshot_date to remittance date on output**

- **Description:** Verify outgoing `BillableUsage.snapshotDate` is set to remittance timestamp for marketplace window compliance.  
- **Setup:**  
  - Publish tally with historical snapshot date
- **Action:**  
  - Consume Kafka message
- **Verification:**  
  - `snapshotDate` on emitted message matches remittance creation time (not original tally date)
- **Expected Result:**  
  - Timestamp adjusted to meet marketplace billing window requirements

---

## Contract Coverage

Java component tests in `ContractCoverageComponentTest` (`swatch-billable-usage/ct`); each test uses `@TestPlanName("billable-usage-contract-coverage-TC00N")`. Contract API responses are stubbed via Wiremock (`ContractsWiremockService`).

**billable-usage-contract-coverage-TC001 - Skip processing when contract-enabled product has no contract**

- **Description:** Verify contract-enabled products without a contract record are not billed.  
- **Setup:**  
  - Product: `rosa` (contract-enabled)  
  - No contract mock response
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Account remittance API returns `remittedValue = 0`  
  - No tally remittance row created  
  - No Kafka message emitted
- **Expected Result:**  
  - Processing skipped when contract is missing; no billable usage produced

**billable-usage-contract-coverage-TC002 - Contract fully covers usage (zero remittance)**

- **Description:** Verify when contract value ≥ current_total, remittance value is 0.  
- **Setup:**  
  - ROSA contract with metric value 10  
  - Tally `current_total` = 4
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance API returns `remittedValue` = 0
- **Expected Result:**  
  - Applicable usage floored at zero

**billable-usage-contract-coverage-TC003 - Contract partially covers usage**

- **Description:** Verify only usage above contract amount is remitted.  
- **Setup:**  
  - ROSA contract metric value = 3 (billable units)  
  - Tally `current_total` = 4 Instance-hours
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance `remitted_pending_value` = 1 (delta after contract conversion)
- **Expected Result:**  
  - Contract subtracted before remittance delta calculation

**billable-usage-contract-coverage-TC004 - Contract with no metrics still allows billing**

- **Description:** Verify a contract record without metric dimensions does not block billing.  
- **Setup:**  
  - Mock contracts API response with empty metrics array  
  - Tally with usage value = 4
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance created with full usage value
- **Expected Result:**  
  - Contract coverage total = 0

**billable-usage-contract-coverage-TC005 - Create GRATIS remittance without Kafka emission**

- **Description:** Verify gratis contract coverage creates remittance with `gratis` status but no Kafka message.  
- **Setup:**  
  - Contract starting in current month  
  - Product: `ansible-aap-managed`
- **Action:**  
  - Publish tally in contract start month
- **Verification:**  
  - Remittance status = `gratis`  
  - No message on `billable-usage` topic
- **Expected Result:**  
  - No billable usage message emitted for gratis remittance

**billable-usage-contract-coverage-TC006 - GRATIS not applied in month after contract start**

- **Description:** Verify gratis treatment only applies in the contract start month.  
- **Setup:**  
  - Contract starting in month N  
  - Tally in month N+1
- **Action:**  
  - Publish tally for next month
- **Verification:**  
  - Remittance status = `pending` (not gratis)  
  - Kafka message emitted
- **Expected Result:**  
  - Gratis treatment does not apply after contract start month

**billable-usage-contract-coverage-TC007 - Resolve AWS dimension as contract metric ID**

- **Description:** Verify AWS billing provider uses the configured AWS dimension (not the SWATCH metric ID) for contract lookup.  
- **Setup:**  
  - Mock contracts API expects contract query with AWS dimension, not SWATCH metric ID
- **Action:**  
  - Publish AWS tally for ROSA Cores
- **Verification:**  
  - Mock contracts API received correct metric ID in contract API call
- **Expected Result:**  
  - Provider-specific dimension mapping applied

**billable-usage-contract-coverage-TC008 - Handle contracts API unavailable**

- **Description:** Verify transient contracts API failure does not create billable remittance or emit usage.  
- **Setup:**  
  - Mock contracts API returns HTTP 500 for contract endpoint  
  - Contract-enabled product
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Account remittance API returns `remittedValue = 0`  
  - No tally remittance row created  
  - No Kafka message emitted  
  - Contract service error logged
- **Expected Result:**  
  - Contract lookup failure prevents billable remittance and billing

**billable-usage-contract-coverage-TC009 - Multi-contract coverage fully covers usage**

- **Description:** Verify summed capacities across contracts with `licenseId` still suppress remittance when usage is within total coverage.  
- **Setup:**  
  - Two ROSA contracts with licenseIds; combined coverage = 6 Instance-hours  
  - Tally `current_total` = 5
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Account remittance `remittedValue` = 0  
  - No Kafka message emitted (blocked by known bug: SWATCH-5443 — zero remitted still produces BillableUsage; assertion commented in CT until fixed)
- **Expected Result:**  
  - Prepaid coverage math unchanged (sum of capacities)

**billable-usage-contract-coverage-TC010 - Overage remittance stamps newest licenseId**

- **Description:** Verify PAYG overage selects the newest agreement licenseId and stamps remittance + BillableUsage.  
- **Setup:**  
  - Two ROSA contracts (coverage 2+2), different start dates and licenseIds  
  - Tally `current_total` = 5 (overage 1)
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - One remittance with `remitted_pending_value` = 1 and selected (newest) `licenseId`  
  - One Kafka BillableUsage with same `licenseId` and value 1
- **Expected Result:**  
  - Single overage remittance allocated to newest agreement

**billable-usage-contract-coverage-TC011 - Mixed licensed and unlicensed contracts**

- **Description:** Verify coverage sums all contracts; overage licenseId comes only from licensed set.  
- **Setup:**  
  - Older licensed contract (coverage 2) + newer unlicensed contract (coverage 2)  
  - Tally `current_total` = 5 (overage 1)
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance pending value = 1 with licensed agreement's `licenseId`  
  - Kafka BillableUsage carries the same `licenseId`
- **Expected Result:**  
  - Mixed set: coverage includes both; selection ignores null licenseId

**billable-usage-contract-coverage-TC012 - Mixed gratis-eligible and established agreements**

- **Description:** Verify multiple contracts with `licenseId` still require every active agreement to be gratis-eligible; if any established (non-gratis) agreement exists, overage is billed to the newest `licenseId`.  
- **Setup:**  
  - Product: `ansible-aap-managed` (gratis-enabled metric)  
  - Established agreement at month start (00:00 UTC) + mid-month agreement (newer `licenseId`); combined coverage = 2  
  - Tally `current_total` = 3 (overage 1) in start month
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance status = `pending`, value = 1, `licenseId` = mid-month agreement  
  - Kafka BillableUsage emitted with same `licenseId`
- **Expected Result:**  
  - Newest agreement does not make the period gratis by itself; one established agreement disqualifies gratis

**billable-usage-contract-coverage-TC013 - All agreements gratis-eligible**

- **Description:** Verify when every active agreement starts after month start, overage remittance is `gratis` and stamps the newest `licenseId` (no Kafka emission).  
- **Setup:**  
  - Product: `ansible-aap-managed`  
  - Two mid-month agreements with different start dates/licenseIds; combined coverage = 2  
  - Tally `current_total` = 3 (overage 1) in start month
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - Remittance status = `gratis`, value = 1, `licenseId` = newest agreement  
  - No message on `billable-usage` topic
- **Expected Result:**  
  - All-agreements gratis rule unchanged; selected overage `licenseId` still recorded on remittance

**billable-usage-contract-coverage-TC014 - Same startDate uses lexicographic licenseId tie-break**

- **Description:** Verify when two licensed agreements share the same `startDate`, overage uses the lexicographically smaller `licenseId` regardless of contracts API order.  
- **Setup:**  
  - Two ROSA contracts with identical start/end dates (coverage 2+2)  
  - Larger `licenseId` listed first, smaller second  
  - Tally `current_total` = 5 (overage 1)
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - One remittance with `remitted_pending_value` = 1 and smaller `licenseId`  
  - One Kafka BillableUsage with the same smaller `licenseId` and value 1
- **Expected Result:**  
  - Deterministic overage allocation when start dates tie (lexicographically smaller wins)

---

## Contract Adjustment Remittance

Java component tests in `ContractAdjustmentComponentTest` (`swatch-billable-usage/ct`); each test uses `@TestPlanName("billable-usage-contract-adjustment-TC00N")`. Mid-month contract changes are stubbed via Wiremock; expected remittance values use `BillableUsageRemittanceExpectations`.

**billable-usage-contract-adjustment-TC001 - Remove contract mid-month**

- **Description:** Verify removing a contract mid-month does not change remittance already recorded; additional usage after contract restore applies the adjustment formula.
- **Setup:**
  - Wiremock returns ROSA contract with equal Cores and Instance-hours coverage (6 billable units per metric)
  - Billing account ID generated for the test
- **Action:**
  - Phase 1: Publish tally increment 100 → verify initial remittance per metric
  - Phase 2: Stub no contract; re-tally with zero increment
  - Phase 3: Restore contract; publish second increment (month total 200)
- **Verification:**
  - Phase 2 remittance unchanged from phase 1
  - Phase 3 remittance matches `BillableUsageRemittanceExpectations.expectedRemittanceAfterUsageIncrease`
- **Expected Result:**
  - Cores: initial 76, final 176; Instance-hours: initial 94, final 194

**billable-usage-contract-adjustment-TC002 - Add contract mid-month**

- **Description:** Verify adding a second contract mid-month keeps remittance at the first contract value until usage exceeds combined coverage.
- **Setup:**
  - Wiremock returns ROSA contract with coverage 10 billable units per metric
  - Billing account ID generated for the test
- **Action:**
  - Phase 1: Publish tally increment 100 → verify initial remittance
  - Phase 2: Stub two contracts (10 + 100 coverage); re-tally with zero increment
  - Phase 3: Publish second increment (month total 200)
  - Phase 4: Publish large third increment (month total 501)
- **Verification:**
  - Phases 2 and 3 remittance unchanged from phase 1
  - Phase 4 remittance matches combined-contract adjustment formula
- **Expected Result:**
  - Cores: initial 60, final 64; Instance-hours: initial 90, final 391

---

## Hourly Aggregation (Kafka Streams)

**billable-usage-aggregation-TC001 - Aggregate multiple tallies into single hourly message**

- **Description:** Verify multiple billable usage events in the same hour/org/account/metric are summed in one aggregate.  
- **Setup:**  
  - Publish 3 tally snapshots with incremental `current_total` (10, 16, 23)
- **Action:**  
  - Trigger flush: `POST /internal/rpc/topics/flush`
- **Verification:**  
  - One message on `billable-usage-hourly-aggregate`  
  - `totalValue` = sum of individual billable values
- **Expected Result:**  
  - Hourly rollup for marketplace producers

**billable-usage-aggregation-TC002 - Keep latest non-null licenseId on hourly aggregate**

- **Description:** Verify hourly aggregation carries `licenseId` on the aggregate body and keeps the latest non-null value when multiple BillableUsage messages share the same key.  
- **Setup:**  
  - Publish three BillableUsage messages for the same org/product/metric/billingAccountId with values 1, 2, 3 and licenseIds A, null, B  
- **Action:**  
  - Trigger flush: `POST /internal/rpc/topics/flush`
- **Verification:**  
  - One message on `billable-usage-hourly-aggregate`  
  - `licenseId` = B (latest non-null)  
  - `totalValue` = 6  
  - Aggregate key dimensions unchanged (no licenseId on key)
- **Expected Result:**  
  - Latest non-null license wins; null does not create a separate aggregate

**billable-usage-aggregation-TC003 - Retain prior licenseId when later usage is null**

- **Description:** Verify a later BillableUsage with null `licenseId` does not clear a previously set aggregate license.  
- **Setup:**  
  - Publish BillableUsage with licenseId A, then another for the same key with null licenseId  
- **Action:**  
  - Trigger flush: `POST /internal/rpc/topics/flush`
- **Verification:**  
  - One hourly aggregate with `licenseId` = A and summed `totalValue`
- **Expected Result:**  
  - Legacy/null license messages do not wipe the license already carried on the aggregate

---

## Status Consumer (marketplace feedback)

**billable-usage-status-TC001 - Align remittance licenses to status aggregate licenseId**

- **Description:** Verify when a status aggregate carries a non-null `licenseId`, all referenced remittance rows are updated to that final metering license (even if they previously had a different or null license).  
- **Setup:**  
  - Create two pending remittances for the **same** org/billingAccountId (same hourly aggregate key)  
  - Stamp them with licenseIds A then B via successive tallies as contracts change  
- **Action:**  
  - Publish `billable-usage.status` aggregate referencing both remittance UUIDs with `status=SUCCEEDED` and `licenseId=B`  
- **Verification:**  
  - Both remittances reach `SUCCEEDED`  
  - Both remittances have `licenseId=B`  
- **Expected Result:**  
  - Remittance ledger matches the license actually used for AWS metering

**billable-usage-status-TC002 - Null status licenseId clears remittance license**

- **Description:** Verify a status update with null `licenseId` updates status/billedOn and sets remittance `license_id` to null (same value as the metering status payload).  
- **Setup:**  
  - Create a pending remittance stamped with licenseId A  
- **Action:**  
  - Publish status aggregate with `status=SUCCEEDED` and null `licenseId`  
- **Verification:**  
  - Remittance status = `SUCCEEDED`  
  - Remittance `licenseId` is null  
- **Expected Result:**  
  - Remittance license always mirrors the status aggregate licenseId

**billable-usage-status-TC003 - Update remittance with SUCCEEDED status**

- **Description:** Verify status consumer marks remittance SUCCEEDED and sets billedOn.  
- **Setup:**  
  - Create a pending remittance (no contract coverage)  
- **Action:**  
  - Publish status aggregate with `status=SUCCEEDED` and billedOn  
- **Verification:**  
  - Remittance status = `SUCCEEDED`  
  - `billedOn` is set near the published timestamp  
- **Expected Result:**  
  - Successful marketplace feedback updates remittance lifecycle fields

**billable-usage-status-TC004 - Update remittance with FAILED status**

- **Description:** Verify status consumer marks remittance FAILED and sets errorCode.  
- **Setup:**  
  - Create a pending remittance (no contract coverage)  
- **Action:**  
  - Publish status aggregate with `status=FAILED` and `errorCode=SUBSCRIPTION_NOT_FOUND`  
- **Verification:**  
  - Remittance status = `FAILED`  
  - `errorCode` = `SUBSCRIPTION_NOT_FOUND`  
- **Expected Result:**  
  - Failed marketplace feedback updates remittance lifecycle fields

---

## Negative and Resilience

**billable-usage-negative-TC001 - Service survives null tally message**

- **Description:** Verify null payload to tally topic is silently dropped without crashing the service.  
- **Action:**  
  - Attempt to produce null to tally topic
- **Verification:**  
  - Null message silently dropped by consumer (no billable usage produced)
  - Service healthy after subsequent valid messages
- **Expected Result:**  
  - Invalid tally message does not crash the service

**billable-usage-negative-TC002 - Service survives malformed tally deserialization**

- **Description:** Verify a message that cannot be deserialized as TallySummary does not crash the consumer.  
- **Action:**  
  - Publish malformed message (wrong JSON type)
- **Verification:**  
  - Service remains ready  
  - No billable usage produced for the malformed message (subsequent valid tally processed normally)
- **Expected Result:**  
  - Malformed tally message does not crash the service

