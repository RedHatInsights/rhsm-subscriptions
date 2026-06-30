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

**billable-usage-contract-coverage-TC001 - Skip processing when contract-enabled product has no contract**

- **Description:** Verify contract-enabled products without a contract record are not billed.  
- **Setup:**  
  - Product: `rosa` (contract-enabled)  
  - No contract mock response
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - No remittance created  
  - No Kafka message emitted
- **Expected Result:**  
  - Processing skipped when contract is missing

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

- **Description:** Verify transient contracts API failure does not create remittance or emit usage.  
- **Setup:**  
  - Mock contracts API returns HTTP 500 for contract endpoint  
  - Contract-enabled product
- **Action:**  
  - Publish tally summary
- **Verification:**  
  - No remittance created  
  - Contract service error logged
- **Expected Result:**  
  - Contract lookup failure prevents remittance and billing

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

---

## Status Consumer

**billable-usage-status-TC001 - Update remittance to FAILED with error_code**

- **Description:** Verify marketplace failure updates remittance with error details.  
- **Setup:**  
  - Pending remittance exists  
  - Status message with `status=failed`, `error_code=subscription_not_found`
- **Action:**  
  - Publish to status topic
- **Verification:**  
  - Remittance `status=failed`, `error_code=subscription_not_found`
- **Expected Result:**  
  - Failure auditable for support and retry logic

**billable-usage-status-TC002 - Ignore status message with null status**

- **Description:** Verify messages without status field are dropped.  
- **Setup:**  
  - `BillableUsageAggregate` with `status=null`
- **Action:**  
  - Publish to status topic
- **Verification:**  
  - Remittance unchanged  
  - Error logged
- **Expected Result:**  
  - Status message ignored; remittance unchanged

**billable-usage-status-TC003 - Do not downgrade SUCCEEDED to FAILED**

- **Description:** Verify finalized succeeded remittances are immutable.  
- **Setup:**  
  - Remittance with `status=succeeded`
- **Action:**  
  - Publish failed status update for same UUID
- **Verification:**  
  - Status remains `succeeded`
- **Expected Result:**  
  - Succeeded remittance remains unchanged

**billable-usage-status-TC004 - Do not upgrade FAILED to SUCCEEDED**

- **Description:** Verify finalized failed remittances are immutable.  
- **Setup:**  
  - Remittance with `status=failed`
- **Action:**  
  - Publish succeeded status update
- **Verification:**  
  - Status remains `failed`
- **Expected Result:**  
  - Finalized remittances not overwritten

**billable-usage-status-TC005 - Accept marketplace_rate_limit error code**

- **Description:** Verify AWS throttling error is recorded on remittance.  
- **Setup:**  
  - Pending remittance  
  - Status with `error_code=marketplace_rate_limit`
- **Action:**  
  - Publish to status topic
- **Verification:**  
  - `error_code=marketplace_rate_limit` on remittance
- **Expected Result:**  
  - Rate limit error recorded on remittance

**billable-usage-status-TC006 - Update multiple remittance UUIDs in single aggregate**

- **Description:** Verify one status message can update multiple remittances in an hourly aggregate.  
- **Setup:**  
  - Hourly aggregate with 2+ remittance UUIDs
- **Action:**  
  - Publish status with all UUIDs
- **Verification:**  
  - All referenced remittances updated
- **Expected Result:**  
  - Batch status update support

---

## Negative and Resilience

**billable-usage-negative-TC001 - Service survives null tally message**

- **Description:** Verify null payload to tally topic is rejected without crashing the service.  
- **Action:**  
  - Attempt to produce null to tally topic
- **Verification:**  
  - Null message rejected with client error  
  - Service healthy after subsequent valid messages
- **Expected Result:**  
  - Invalid tally message does not crash the service

**billable-usage-negative-TC002 - Service survives malformed tally deserialization**

- **Description:** Verify invalid JSON on tally topic does not crash consumer.  
- **Action:**  
  - Publish malformed message
- **Verification:**  
  - Service remains ready  
  - No remittance created
- **Expected Result:**  
  - Malformed tally message does not crash the service

