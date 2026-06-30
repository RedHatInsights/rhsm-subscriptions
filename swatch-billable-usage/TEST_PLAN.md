# Introduction

The **swatch-billable-usage** service is a core billing component within the Subscription Watch (SWATCH) platform. It consumes tally summaries, applies contract coverage and billing factors, maintains a remittance ledger to prevent double-billing, emits billable usage events to Kafka, aggregates usage into hourly windows, and processes marketplace status feedback.

This document defines the **component-level test plan** for `swatch-billable-usage`.

**Purpose:** Ensure `swatch-billable-usage` is functionally correct, reliable, and meets billing requirements at the component boundary, independently of full end-to-end marketplace submission.

**Scope:**

* Tally summary ingestion and PAYG eligibility filtering
* Billable usage calculation (contract coverage, billing factor, remittance delta)
* Contract coverage integration with `swatch-contracts` (mocked in component tests)
* Remittance persistence and lifecycle (`billable_usage_remittance` table)
* Kafka message production and consumption (`tally`, `billable-usage`, `billable-usage-hourly-aggregate`, `billable-usage.status`)
* Kafka Streams hourly aggregation
* Internal Admin API operations (query, reset, flush, delete, purge, reconcile)
* Remittance purge task processing

**Out of scope:**

* End-to-end marketplace API submission (covered by `swatch-producer-aws`, `swatch-producer-azure`, and IQE integration tests)
* `swatch-tally` tally computation logic
* `swatch-contracts` contract creation and sync logic (covered by `swatch-contracts/TEST_PLAN.md`)
* Stage/prod long-run heartbeat tests
* Performance, load, and chaos testing

**Assumptions:**

* `swatch-billable-usage` is deployed with access to the shared `rhsm-subscriptions` PostgreSQL database
* Kafka topics are available and configured for the deployment environment
* `swatch-contracts` REST API is mockable in component tests
* Product configuration (`swatch-product-configuration`) is stable for reference products used in tests

**Constraints:**

* Component tests validate the service in isolation with mocked external dependencies
* Tests must be runnable locally and in ephemeral OpenShift environments

---

# Test Strategy

**Test approach:**

* **Risk-based prioritization** — focus on billing calculation correctness, double-billing prevention, and contract coverage handling
* **Automated component tests** with mocked external dependencies and message injection
* **Verification via multiple channels** — Kafka topic assertions, internal REST API queries, and direct database inspection where needed


# Test Cases

## Tally Summary Ingestion

**billable-usage-tally-ingestion-TC001 - Process a valid hourly PAYG tally summary for AWS**
- **Description:** Verify that a valid hourly tally summary for a PAYG-eligible product produces billable usage and a remittance record.  
- **Setup:**  
  - Mock contracts API: no contract coverage for org/product  
  - Kafka topic `platform.rhsm-subscriptions.tally` available  
  - Prepare `TallySummary` with HOURLY granularity, specific billing provider (`aws`), billing account, SLA, and usage  
- **Action:**  
  - Publish tally summary to Kafka topic  
- **Verification:**  
  - Consume message from `platform.rhsm-subscriptions.billable-usage`  
  - Query remittance: `GET /internal/remittance/accountRemittances/{tally_id}`  
- **Expected Result:**  
  - One `BillableUsage` message emitted with correct org, product, metric, billing provider, and billable value  
  - Remittance record created with status `pending`  
  - `remitted_pending_value` stored in metric units  

**billable-usage-tally-ingestion-TC002 - Process a valid hourly PAYG tally summary for Azure**
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

**billable-usage-tally-ingestion-TC003 - Ignore non-hourly (DAILY) granularity snapshots**
- **Description:** Verify only HOURLY granularity snapshots produce billable usage; DAILY snapshots are filtered out.  
- **Setup:**  
  - Prepare two tally summaries: one DAILY, one HOURLY, same org/product/metric  
- **Action:**  
  - Publish both to tally topic  
- **Verification:**  
  - Consume from `billable-usage` topic  
- **Expected Result:**  
  - Exactly one billable usage message  
  - Message `tally_id` matches the HOURLY snapshot  

**billable-usage-tally-ingestion-TC004 - Ignore snapshots with ANY billing provider**
- **Description:** Verify snapshots with `billing_provider=_ANY` are not processed.  
- **Setup:**  
  - Prepare tally summary with `BillingProvider.ANY`  
- **Action:**  
  - Publish to tally topic  
- **Verification:**  
  - No message on `billable-usage` topic  
  - No remittance created  
- **Expected Result:**  
  - Snapshot silently filtered as non-PAYG-eligible  

**billable-usage-tally-ingestion-TC005 - Ignore snapshots with ANY billing account**
- **Description:** Verify snapshots with `billing_account_id=_ANY` are not processed.  
- **Setup:**  
  - Prepare tally summary with `billing_account_id=_ANY`  
- **Action:**  
  - Publish to tally topic  
- **Verification:**  
  - No billable usage or remittance created  
- **Expected Result:**  
  - Snapshot filtered  

**billable-usage-tally-ingestion-TC006 - Ignore non-PAYG-eligible products**
- **Description:** Verify products without marketplace metric dimensions are not billed.  
- **Setup:**  
  - Prepare tally summary for a non-PAYG product (no `awsDimension`/`azureDimension`/`rhmMetricId`)  
- **Action:**  
  - Publish to tally topic  
- **Verification:**  
  - No billable usage emitted  
- **Expected Result:**  
  - Non-PAYG product produces no billable usage  

**billable-usage-tally-ingestion-TC007 - Ignore unsupported metric IDs**
- **Description:** Verify tally measurements with unknown metric IDs are skipped without service failure.  
- **Setup:**  
  - Prepare tally summary containing an invalid/unsupported `metric_id`  
- **Action:**  
  - Publish to tally topic  
- **Verification:**  
  - Service remains healthy  
  - No remittance for invalid metric  
- **Expected Result:**  
  - Warning logged; valid metrics in same summary still processed if present  

**billable-usage-tally-ingestion-TC008 - Ignore HARDWARE_MEASUREMENT_TYPE TOTAL duplicates**
- **Description:** Verify `HardwareMeasurementType.TOTAL` measurements are filtered to prevent duplicate billing.  
- **Setup:**  
  - Prepare tally snapshot with both regular and TOTAL hardware measurement types  
- **Action:**  
  - Publish to tally topic  
- **Verification:**  
  - Only non-TOTAL measurements produce billable usage  
- **Expected Result:**  
  - One billable usage per non-TOTAL measurement  

**billable-usage-tally-ingestion-TC009 - Map one tally summary to multiple billable usages**
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

**billable-usage-tally-ingestion-TC010 - Reject invalid snapshot date without service crash**
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

**billable-usage-billing-calculation-TC001 - Calculate billable value with billing factor below 1**
- **Description:** Verify metric-to-billable conversion using billing factor and ceiling rounding (ROSA Cores, factor 0.25).  
- **Setup:**  
  - No contract coverage  
  - Tally `current_total` = 20 Cores  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - Kafka `billable-usage` message `value` = 5.0 (20 × 0.25)  
  - Remittance `remitted_pending_value` = 20.0 (metric units)  
- **Expected Result:**  
  - Billable value in billing units; remittance stored in metric units  

**billable-usage-billing-calculation-TC002 - Calculate billable value with billing factor equal to 1**
- **Description:** Verify no scaling when billing factor is 1.0 (RHEL PAYG addon vCPUs).  
- **Setup:**  
  - No contract coverage  
  - Tally `current_total` = 12 vCPUs  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - Kafka message `value` = 12.0  
  - Remittance `remitted_pending_value` = 12.0  
- **Expected Result:**  
  - Metric value equals billable value  

**billable-usage-billing-calculation-TC003 - Apply ceiling rounding to billable units**
- **Description:** Verify fractional billable results are rounded up (e.g. 7.5 → 8).  
- **Setup:**  
  - Construct usage where applicable metric delta × billing factor yields a non-integer (e.g. 30 cores × 0.25 = 7.5)  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - Kafka message `value` = 8 (ceil of 7.5)  
- **Expected Result:**  
  - Integer billable value per marketplace requirement  

**billable-usage-billing-calculation-TC004 - Remit only the delta when current_total increases**
- **Description:** Verify second tally in same month remits only the difference from prior remittances.  
- **Setup:**  
  - First tally: `current_total` = 6, remittance succeeds  
  - Second tally: `current_total` = 10  
- **Action:**  
  - Publish both tally summaries  
- **Verification:**  
  - Two remittance records  
  - Second remittance `remitted_pending_value` = 4 (10 − 6)  
  - Second Kafka message `value` = 4  
- **Expected Result:**  
  - No double-billing of the first 6 units  

**billable-usage-billing-calculation-TC005 - No new remittance when current_total decreases**
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

**billable-usage-billing-calculation-TC006 - Exclude failed remittances from total remitted calculation**
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

**billable-usage-billing-calculation-TC007 - No remittance when applicable usage is zero**
- **Description:** Verify no remittance row or Kafka message when calculated delta is zero.  
- **Setup:**  
  - Contract fully covers usage OR `current_total` equals already remitted  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - No new remittance created  
  - No Kafka message emitted  
- **Expected Result:**  
  - Log: "Nothing to remit. Remittance record will not be created."  

**billable-usage-billing-calculation-TC008 - Separate accumulation periods by month**
- **Description:** Verify usage from different months creates separate remittances, not aggregated.  
- **Setup:**  
  - Tally with `snapshot_date` in last month  
  - Tally with `snapshot_date` in current month  
  - Same org, product, metric, billing account  
- **Action:**  
  - Publish both tally summaries  
- **Verification:**  
  - Two remittances with different `accumulation_period` (e.g. `2025-05`, `2025-06`)  
- **Expected Result:**  
  - Monthly billing windows isolated  

**billable-usage-billing-calculation-TC009 - Update snapshot_date to remittance date on output**
- **Description:** Verify outgoing `BillableUsage.snapshotDate` is set to remittance timestamp for marketplace window compliance.  
- **Setup:**  
  - Publish tally with historical snapshot date  
- **Action:**  
  - Consume Kafka message  
- **Verification:**  
  - `snapshotDate` on emitted message matches remittance creation time (not original tally date)  
- **Expected Result:**  
  - Timestamp adjusted per SWATCH AWS/Azure marketplace window requirements  

---

## Contract Coverage

**billable-usage-contract-coverage-TC001 - Process PAYG product without contract (non-contract-enabled)**
- **Description:** Verify pure PAYG products bill full usage without calling contracts API.  
- **Setup:**  
  - Product: `rhacs` (not contract-enabled)  
  - No contract mock required  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - Remittance created with full usage value  
  - Contracts API not invoked  
- **Expected Result:**  
  - No contract coverage applied; full usage billed  

**billable-usage-contract-coverage-TC002 - Skip processing when contract-enabled product has no contract**
- **Description:** Verify contract-enabled products without a contract record are not billed.  
- **Setup:**  
  - Product: `rosa` (`contractEnabled: true`)  
  - No contract mock response  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - No remittance created  
  - No Kafka message emitted  
- **Expected Result:**  
  - Processing skipped when contract is missing  

**billable-usage-contract-coverage-TC003 - Contract fully covers usage (zero remittance)**
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

**billable-usage-contract-coverage-TC004 - Contract partially covers usage**
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

**billable-usage-contract-coverage-TC005 - Contract with no metrics still allows billing**
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

**billable-usage-contract-coverage-TC006 - Apply contract coverage with billing factor conversion**
- **Description:** Verify contract values (billable units) are converted to metric units before subtraction.  
- **Setup:**  
  - ROSA: contract = 1 four_vcpu_hour, tally = 8 Cores, billing factor = 0.25  
- **Action:**  
  - Publish tally summary  
- **Verification:**  
  - Kafka `value` = 1.0 ((8 × 0.25) − 1.0)  
- **Expected Result:**  
  - Contract converted: 1 billable unit / 0.25 = 4 metric units subtracted  

**billable-usage-contract-coverage-TC007 - Create GRATIS remittance without Kafka emission**
- **Description:** Verify gratis contract coverage creates remittance with `gratis` status but no Kafka message.  
- **Setup:**  
  - Contract starting in current month (gratis eligible per SWATCH-2571)  
  - Product: `ansible-aap-managed`  
- **Action:**  
  - Publish tally in contract start month  
- **Verification:**  
  - Remittance status = `gratis`  
  - No message on `billable-usage` topic  
- **Expected Result:**  
  - No billable usage message emitted for gratis remittance  

**billable-usage-contract-coverage-TC008 - GRATIS not applied in month after contract start**
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

**billable-usage-contract-coverage-TC009 - Resolve AWS dimension as contract metric ID**
- **Description:** Verify AWS billing provider uses the configured AWS dimension (not the SWATCH metric ID) for contract lookup.  
- **Setup:**  
  - Mock contracts API expects contract query with AWS dimension, not SWATCH metric ID  
- **Action:**  
  - Publish AWS tally for ROSA Cores  
- **Verification:**  
  - Mock contracts API received correct metric ID in contract API call  
- **Expected Result:**  
  - Provider-specific dimension mapping applied  

**billable-usage-contract-coverage-TC010 - Handle contracts API unavailable**
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

**billable-usage-contract-coverage-TC011 - Filter expired and not-yet-started contracts**
- **Description:** Verify only contracts valid for `snapshot_date` contribute to coverage.  
- **Setup:**  
  - Multiple contracts: one expired, one future, one active  
- **Action:**  
  - Publish tally with specific snapshot date  
- **Verification:**  
  - Coverage total reflects only active contract  
- **Expected Result:**  
  - Only contracts valid for the snapshot date contribute to coverage  

---

## Remittance Tracking

**billable-usage-remittance-TC001 - Create remittance with PENDING status on new billable usage**
- **Description:** Verify remittance row is persisted when `remittedValue > 0`.  
- **Setup:**  
  - Publish tally producing non-zero billable delta  
- **Action:**  
  - Query remittance by tally ID  
- **Verification:**  
  - Row exists in `billable_usage_remittance`  
  - `status` = `pending`  
  - `uuid` matches Kafka message  
  - `tally_id` references source tally snapshot  
- **Expected Result:**  
  - Complete audit trail for billing event  

**billable-usage-remittance-TC002 - Store remitted value in metric units**
- **Description:** Verify `remitted_pending_value` is always in metric units regardless of billing factor.  
- **Setup:**  
  - ROSA tally with billing factor 0.25  
- **Action:**  
  - Query remittance  
- **Verification:**  
  - `remitted_pending_value` = tally metric value (not billable units)  
- **Expected Result:**  
  - Consistent metric-unit storage per architecture docs  

**billable-usage-remittance-TC003 - Unique remittance per tally snapshot**
- **Description:** Verify each tally snapshot creates at most one remittance per metric.  
- **Setup:**  
  - Single tally with one metric  
- **Action:**  
  - Query `GET /internal/remittance/accountRemittances/{tally_id}`  
- **Verification:**  
  - Exactly one remittance returned  
- **Expected Result:**  
  - 1:1 tally-to-remittance for billable events  

**billable-usage-remittance-TC004 - Isolate remittances by billing account**
- **Description:** Verify remittances for different billing accounts are tracked independently.  
- **Setup:**  
  - Two tallies, same org/product, different `billing_account_id`  
- **Action:**  
  - Publish both  
- **Verification:**  
  - Two separate remittance records  
  - Delta calculation per billing account  
- **Expected Result:**  
  - No cross-account remittance aggregation  

**billable-usage-remittance-TC005 - Isolate remittances by organization**
- **Description:** Verify remittances for different orgs are independent.  
- **Setup:**  
  - Two tallies, different `org_id`, same product  
- **Action:**  
  - Publish both  
- **Verification:**  
  - One remittance per org  
  - Separate hourly aggregates per org  
- **Expected Result:**  
  - Org-level billing isolation  

---

## Kafka Emission

**billable-usage-kafka-emission-TC001 - Emit billable usage to correct topic**
- **Description:** Verify billable usage is published to `platform.rhsm-subscriptions.billable-usage`.  
- **Setup:**  
  - Kafka consumer subscribed to billable-usage topic  
- **Action:**  
  - Publish valid tally summary  
- **Verification:**  
  - Message received with expected schema fields: `uuid`, `org_id`, `product_id`, `metric_id`, `value`, `billing_factor`, `status`  
- **Expected Result:**  
  - Message ready for downstream aggregation without further calculation  

**billable-usage-kafka-emission-TC002 - Do not emit when status is GRATIS**
- **Description:** Verify gratis remittances are persisted but not published to Kafka.  
- **Setup:**  
  - Gratis-eligible contract and tally  
- **Action:**  
  - Publish tally  
- **Verification:**  
  - Remittance with `gratis` status exists  
  - No Kafka message  
- **Expected Result:**  
  - Gratis remittance persisted without Kafka emission  

**billable-usage-kafka-emission-TC003 - Do not emit when contract lookup fails**
- **Description:** Verify no Kafka message when contract coverage throws.  
- **Setup:**  
  - Contract-enabled product, no contract  
- **Action:**  
  - Publish tally  
- **Verification:**  
  - No Kafka message  
- **Expected Result:**  
  - Processing short-circuited before producer call  

**billable-usage-kafka-emission-TC004 - Emit non-negative integer billable value**
- **Description:** Verify all emitted `value` fields are non-negative integers in billing units.  
- **Setup:**  
  - Various tally values including edge cases (0, fractional results)  
- **Action:**  
  - Publish tallies  
- **Verification:**  
  - All Kafka messages have integer `value` ≥ 0  
- **Expected Result:**  
  - Marketplace-ready billing units  

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

**billable-usage-aggregation-TC002 - Separate aggregates per billing account**
- **Description:** Verify different billing accounts produce separate hourly aggregates.  
- **Setup:**  
  - Two tallies, different billing accounts, same org/product  
- **Action:**  
  - Publish both; flush aggregation  
- **Verification:**  
  - Two hourly aggregate messages  
  - Each with correct `totalValue` per account  
- **Expected Result:**  
  - Aggregation key includes `billing_account_id`  

**billable-usage-aggregation-TC003 - Separate aggregates per metric**
- **Description:** Verify different metrics produce separate hourly aggregates.  
- **Setup:**  
  - ROSA tallies for Cores and Instance-hours  
- **Action:**  
  - Publish both; flush  
- **Verification:**  
  - Two aggregates with metric-specific `totalValue`  
- **Expected Result:**  
  - Aggregation key includes `metric_id`  

**billable-usage-aggregation-TC004 - Separate aggregates per product**
- **Description:** Verify different products produce separate hourly aggregates.  
- **Setup:**  
  - Tallies for ROSA and RHEL PAYG addon, same org/account  
- **Action:**  
  - Publish both; flush  
- **Verification:**  
  - Two aggregates with product-specific values  
- **Expected Result:**  
  - Aggregation key includes `product_id`  

**billable-usage-aggregation-TC005 - Flush forces aggregation for low-volume streams**
- **Description:** Verify admin flush RPC emits aggregates before window naturally closes.  
- **Setup:**  
  - Publish tally; do not wait for window close  
- **Action:**  
  - `POST /internal/rpc/topics/flush`  
- **Verification:**  
  - Hourly aggregate message received  
- **Expected Result:**  
  - Flush triggers hourly aggregate emission  

**billable-usage-aggregation-TC006 - Suppress intermediate aggregates until window closes**
- **Description:** Verify only final window result is emitted (not partial aggregates).  
- **Setup:**  
  - Publish tally mid-window  
  - Do not flush  
- **Action:**  
  - Monitor hourly aggregate topic before and after window close  
- **Verification:**  
  - No aggregate before window close + grace period  
  - One aggregate after window closes  
- **Expected Result:**  
  - Only final window result emitted, not partial aggregates  

**billable-usage-aggregation-TC007 - Include remittance UUIDs in aggregate**
- **Description:** Verify hourly aggregate contains list of contributing remittance UUIDs.  
- **Setup:**  
  - Multiple tallies in same aggregation window  
- **Action:**  
  - Flush and consume aggregate  
- **Verification:**  
  - `remittanceUuids` list contains all contributing remittance UUIDs  
- **Expected Result:**  
  - Status feedback can update all contributing remittances  

---

## Status Consumer

**billable-usage-status-TC001 - Update remittance to SUCCEEDED with billed_on**
- **Description:** Verify marketplace success status updates remittance and sets `billed_on`.  
- **Setup:**  
  - Pending remittance exists  
  - Prepare `BillableUsageAggregate` with `status=succeeded` and `billed_on` timestamp  
- **Action:**  
  - Publish to `platform.rhsm-subscriptions.billable-usage.status`  
- **Verification:**  
  - `GET /internal/remittance/accountRemittances/{tally_id}` returns `status=succeeded`  
  - `billed_on` populated  
- **Expected Result:**  
  - Billing confirmation recorded  

**billable-usage-status-TC002 - Update remittance to FAILED with error_code**
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

**billable-usage-status-TC003 - Ignore status message with null status**
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

**billable-usage-status-TC004 - Do not downgrade SUCCEEDED to FAILED**
- **Description:** Verify finalized succeeded remittances are immutable.  
- **Setup:**  
  - Remittance with `status=succeeded`  
- **Action:**  
  - Publish failed status update for same UUID  
- **Verification:**  
  - Status remains `succeeded`  
- **Expected Result:**  
  - Succeeded remittance remains unchanged  

**billable-usage-status-TC005 - Do not upgrade FAILED to SUCCEEDED**
- **Description:** Verify finalized failed remittances are immutable.  
- **Setup:**  
  - Remittance with `status=failed`  
- **Action:**  
  - Publish succeeded status update  
- **Verification:**  
  - Status remains `failed`  
- **Expected Result:**  
  - Finalized remittances not overwritten  

**billable-usage-status-TC006 - Do not update GRATIS remittance status**
- **Description:** Verify gratis remittances are treated as finalized.  
- **Setup:**  
  - Remittance with `status=gratis`  
- **Action:**  
  - Publish status update  
- **Verification:**  
  - Status remains `gratis`  
- **Expected Result:**  
  - Gratis remittance status remains unchanged  

**billable-usage-status-TC007 - Accept marketplace_rate_limit error code**
- **Description:** Verify AWS throttling error is recorded on remittance.  
- **Setup:**  
  - Pending remittance  
  - Status with `error_code=marketplace_rate_limit`  
- **Action:**  
  - Publish to status topic  
- **Verification:**  
  - `error_code=marketplace_rate_limit` on remittance  
- **Expected Result:**  
  - SWATCH-2775 scenario covered  

**billable-usage-status-TC008 - Update multiple remittance UUIDs in single aggregate**
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

## Admin API — Query

**billable-usage-admin-api-TC001 - Query remittances by org and product**
- **Description:** Verify `GET /internal/remittance/accountRemittances` returns monthly remittance summaries.  
- **Setup:**  
  - Existing remittances for org/product  
- **Action:**  
  - `GET /internal/remittance/accountRemittances?orgId={org}&productId={product}`  
- **Verification:**  
  - HTTP 200 with remittance list  
  - Correct `remittedValue`, `remittanceStatus`, filters applied  
- **Expected Result:**  
  - Support and QE visibility into billing state  

**billable-usage-admin-api-TC002 - Require orgId query parameter**
- **Description:** Verify missing `orgId` returns HTTP 400.  
- **Setup:**  
  - Call API without `orgId`  
- **Action:**  
  - `GET /internal/remittance/accountRemittances?productId=rosa`  
- **Verification:**  
  - HTTP 400 Bad Request  
- **Expected Result:**  
  - `"Must provide 'orgId' query parameters."`  

**billable-usage-admin-api-TC003 - Validate beginning before ending date range**
- **Description:** Verify invalid date range returns HTTP 400.  
- **Setup:**  
  - `beginning` > `ending`  
- **Action:**  
  - Query with inverted dates  
- **Verification:**  
  - HTTP 400  
- **Expected Result:**  
  - Date range validation enforced  

**billable-usage-admin-api-TC004 - Query remittances by tally ID**
- **Description:** Verify `GET /internal/remittance/accountRemittances/{tally_id}` returns remittance for specific tally.  
- **Setup:**  
  - Known tally with remittance  
- **Action:**  
  - Query by tally UUID  
- **Verification:**  
  - HTTP 200 with `TallyRemittance` details  
- **Expected Result:**  
  - Traceability from tally to billing  

**billable-usage-admin-api-TC005 - Return 400 when tally ID not found**
- **Description:** Verify unknown tally ID returns Bad Request.  
- **Setup:**  
  - Random UUID not in database  
- **Action:**  
  - Query by tally ID  
- **Verification:**  
  - HTTP 400 with "tally id not found" message  
- **Expected Result:**  
  - Clear error for invalid lookup  

**billable-usage-admin-api-TC006 - Filter remittances by billing provider and account**
- **Description:** Verify optional query filters (`billingProvider`, `billingAccountId`, `metricId`) work.  
- **Setup:**  
  - Remittances for multiple providers/accounts  
- **Action:**  
  - Query with specific filters  
- **Verification:**  
  - Only matching remittances returned  
- **Expected Result:**  
  - Filtered account remittance view  

---

## Admin API — Operations

**billable-usage-admin-api-TC007 - Reset remittance by billing account ID**
- **Description:** Verify reset RPC zeroes `remitted_pending_value` for matching records.  
- **Setup:**  
  - Existing remittance with value > 0  
- **Action:**  
  - `POST /internal/rpc/remittance/reset_billable_usage_remittance?billing_account_ids={id}&product_id={product}&start={}&end={}`  
- **Verification:**  
  - `remitted_pending_value` = 0 in database  
  - HTTP 200 `status=Success`  
- **Expected Result:**  
  - Allows re-billing after operational correction  

**billable-usage-admin-api-TC008 - Reset remittance by org ID**
- **Description:** Verify reset by `org_ids` parameter.  
- **Setup:**  
  - Existing remittance for org  
- **Action:**  
  - Reset with `org_ids` parameter  
- **Verification:**  
  - `remitted_pending_value` = 0  
- **Expected Result:**  
  - Org-scoped reset works  

**billable-usage-admin-api-TC009 - Reject reset with both orgIds and billingAccountIds**
- **Description:** Verify mutually exclusive parameter validation.  
- **Setup:**  
  - Existing remittance  
- **Action:**  
  - Reset with both `org_ids` and `billing_account_ids`  
- **Verification:**  
  - HTTP 400  
  - Remittance unchanged  
- **Expected Result:**  
  - `"Only one of orgIds or billingAccountIds parameters should be specified"`  
 

**billable-usage-admin-api-TC010 - Flush billable usage aggregation topic**
- **Description:** Verify flush RPC triggers Kafka Streams state store flush.  
- **Action:**  
  - `POST /internal/rpc/topics/flush`  
- **Verification:**  
  - HTTP 200 `status=Success`  
  - Pending aggregates emitted  
- **Expected Result:**  
  - Operational tool for test and low-volume environments  


**billable-usage-admin-api-TC011 - Reconcile stuck pending remittances**
- **Description:** Verify reconcile marks stale pending remittances as failed.  
- **Setup:**  
  - Remittance with `status=pending` older than the configured stuck threshold  
- **Action:**  
  - `POST /internal/rpc/remittance/reconcile`  
- **Verification:**  
  - Stale remittance `status=failed`, `error_code=SENDING_TO_AGGREGATE_TOPIC`  
  - Non-stale remittances unchanged  
- **Expected Result:**  
  - Stuck billing state detectable and recoverable  

---

## Remittance Purge Task

**billable-usage-purge-TC001 - Skip purge when retention policy not configured**
- **Description:** Verify purge task processing logs a warning and skips when retention policy is not configured.  
- **Setup:**  
  - No retention policy configured  
- **Action:**  
  - Consume purge task message  
- **Verification:**  
  - No database deletes  
- **Expected Result:**  
  - Safe handling of misconfiguration  

**billable-usage-purge-TC002 - Delete remittances older than cutoff per org**
- **Description:** Verify per-org purge deletes records before cutoff date.  
- **Setup:**  
  - Retention policy = 70 days  
  - Remittances: one old, one recent  
- **Action:**  
  - Consume `EnabledOrgsResponse` on `remittances-purge-task` topic  
- **Verification:**  
  - Old remittance deleted  
  - Recent remittance retained  
- **Expected Result:**  
  - Remittances older than retention cutoff are deleted  

**billable-usage-purge-TC003 - Publish enabled-orgs task for purge fan-out**
- **Description:** Verify purge RPC publishes an enabled-orgs task with the correct target topic.  
- **Setup:**  
  - Retention policy configured  
- **Action:**  
  - `POST /internal/rpc/remittance/purge`  
- **Verification:**  
  - Message on `enabled-orgs-for-tasks` with `targetTopic=remittances-purge-task`  
- **Expected Result:**  
  - Fan-out pattern initiated  

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
