# Test Plan: Billable Usage HOURLY-Only Processing (SWATCH-3790)

## Overview
This test plan ensures that the billable usage service **ONLY processes HOURLY tally summaries** and filters out DAILY summaries. This is critical for accurate billing, as processing DAILY summaries would result in incorrect usage calculations.

## Background
As part of SWATCH-3790, the system will send both HOURLY and DAILY tally summaries to the billable usage service. However, **only HOURLY summaries should be processed** to ensure accurate billing calculations.

## Test Coverage

### 1. Unit Tests - BillableUsageMapperTest

**Location:** `swatch-billable-usage/src/test/java/com/redhat/swatch/billable/usage/services/BillableUsageMapperTest.java`

#### Test: `shouldSkipDailyGranularitySnapshots()` (Line 250-263)
**Purpose:** Verify that DAILY granularity tally snapshots are filtered out at the mapper level.

**Test Steps:**
1. Create a tally summary with DAILY granularity
2. Process it through `BillableUsageMapper.fromTallySummary()`
3. Assert that NO billable usage is produced

**Expected Result:** The mapper returns an empty stream, confirming DAILY snapshots are filtered.

**Coverage:**
- ✅ Validates filtering logic in `BillableUsageMapper.isSnapshotPAYGEligible()`
- ✅ Tests the specific granularity check: `Objects.equals(TallySnapshot.Granularity.HOURLY, snapshot.getGranularity())`

### 2. Integration Tests - TallySummaryMessageConsumerTest

**Location:** `swatch-billable-usage/src/test/java/com/redhat/swatch/billable/usage/services/TallySummaryMessageConsumerTest.java`

#### Test: `testDailySnapshotBypassingFilterIsLogged()` (Line 258-320)
**Purpose:** Verify that if a DAILY snapshot bypasses filtering and is sent to producers, we are notified via logging.

**Test Steps:**
1. Create a DAILY tally snapshot
2. Mock the mapper to simulate filtering failure (allow DAILY through)
3. Send the DAILY snapshot through the consumer
4. Verify billable usage is produced
5. Verify producer logged the billable usage with the DAILY snapshot's tallyId

**Expected Result:** Producer logs billable usage containing the DAILY snapshot's tallyId

**Coverage:**
- ✅ Creates actual DAILY snapshot (not generic BillableUsage)
- ✅ Simulates filtering bug via mocking
- ✅ Verifies full flow: consumer → mapper (bypassed) → service → producer
- ✅ Validates notification mechanism via producer logs

**This is the NOTIFICATION MECHANISM for AC4:** If DAILY snapshots bypass filtering and get sent to producers, the producer logs allow us to:
1. See the billable usage in production logs (INFO level)
2. Extract the tallyId from the log message
3. Trace back to the original snapshot in tally service using tallyId
4. Check the snapshot's granularity field to identify it was DAILY
5. Investigate the filtering failure

---

### 3. Component Tests - TallySummaryConsumerComponentTest

**Location:** `swatch-billable-usage/ct/java/tests/TallySummaryConsumerComponentTest.java`

#### Test: `testOnlyHourlyGranularityIsProcessed()` (Line 95-138)
**Purpose:** End-to-end verification that DAILY summaries are NOT sent to producers.

**Test Steps:**
1. Setup: Configure wiremock with no contract coverage
2. Send a DAILY tally summary to the tally topic
3. Send an HOURLY tally summary to the tally topic
4. Wait for billable usage messages on the billable usage topic
5. Assert exactly 1 message was produced
6. Assert the message came from the HOURLY snapshot (by checking tally ID)

**Expected Result:**
- Only 1 billable usage message is produced
- The message originates from the HOURLY snapshot, NOT the DAILY snapshot

**Coverage:**
- ✅ Tests the complete flow: Kafka consumer → Mapper → Service → Producer
- ✅ Verifies DAILY snapshots never reach the billable usage topic
- ✅ Validates that only HOURLY snapshots produce billable usage

**This is the NEGATIVE TEST for the AC:** If DAILY snapshots start being processed and sent to producers, this test will FAIL with:
```
Expected: <1>
Actual: <2>
Message: "Expected exactly 1 billable usage for HOURLY granularity"
```

---

## Implementation Details

### Filtering Logic
**File:** `swatch-billable-usage/src/main/java/com/redhat/swatch/billable/usage/services/BillableUsageMapper.java`

**Method:** `isSnapshotPAYGEligible()` (Line 50-87)

The filtering occurs at line 59-60:
```java
boolean isHourlyGranularity =
    Objects.equals(TallySnapshot.Granularity.HOURLY, snapshot.getGranularity());
```

When a snapshot is filtered, a debug log is emitted (line 84):
```java
log.debug("Snapshot not billable {}", snapshot);
```

This log can be monitored in production to detect if DAILY snapshots are being sent.

---

## Notification Mechanisms

### 1. Test Failure Notification (Primary)
**When:** A developer breaks the filtering logic (e.g., allows DAILY snapshots)
**What Happens:**
- Component test `testOnlyHourlyGranularityIsProcessed()` FAILS
- CI/CD pipeline blocks the change
- Team is notified via failed test results

### 2. Producer Logging (Defense-in-Depth) ✅ IMPLEMENTED
**File:** `BillingProducer.java:55-62`
**When:** Any billable usage is sent to producers (including if DAILY bypasses filtering)
**What Happens:**
- INFO log: `"Producing billable usage: tallyId={}, product={}, orgId={}, snapshotDate={}, metricId={}, value={}"`
- Contains `tallyId` which can be traced back to original tally snapshot
- If DAILY snapshot bypasses filtering, we can search logs for the tallyId in the tally service to identify it

**Test:** `TallySummaryMessageConsumerTest.testDailySnapshotBypassingFilterIsLogged()`
- Simulates DAILY snapshot bypassing filter
- Verifies billable usage is produced from DAILY snapshot
- Verifies producer logs INFO message containing the DAILY snapshot's tallyId
- **This is the notification mechanism if DAILY snapshots bypass filtering**

**How to detect DAILY snapshots in production:**
1. Observe unexpected billable usage in logs
2. Extract `tallyId` from producer log
3. Search tally service logs for that `tallyId`
4. Check the snapshot's granularity field in tally logs
5. If DAILY → Alert was missed upstream, investigate filtering logic

### 3. Debug Logging (Filtering Layer)
**When:** DAILY snapshots are received and filtered by the mapper
**What Happens:**
- Debug log: "Snapshot not billable {snapshot details}"
- Can be searched in logs to detect misconfiguration

### 4. Production Metrics and Alerting ✅ IMPLEMENTED
**Status:** Fully implemented as part of SWATCH-4013

**Metric Name:** `swatch_billable_usage_snapshots_filtered_total`
**Type:** Counter
**Location:** `BillableUsageMapper.java:108-120`

**Implementation:**
```java
meterRegistry.counter(
    FILTERED_SNAPSHOTS_METRIC,  // "swatch_billable_usage_snapshots_filtered_total"
    "granularity", snapshot.getGranularity().name(),  // DAILY, YEARLY, etc.
    "product", productId,                              // rosa, rhel, etc.
    "reason", "non_hourly"
).increment();
```

**Tags:**
- `granularity`: The snapshot granularity that was filtered (DAILY, YEARLY, MONTHLY, etc.)
- `product`: The product ID (rosa, rhel, etc.)
- `reason`: Why it was filtered (`non_hourly`)

**Unit Tests:**
- `shouldIncrementMetricWhenDailySnapshotIsFiltered()` - Verifies DAILY filtering is tracked
- `shouldIncrementMetricForYearlyGranularity()` - Verifies other granularities are tracked

**Benefits:**
- ✅ Real-time visibility in Grafana/Prometheus
- ✅ Alerts if DAILY snapshots appear in production (see `PROMETHEUS_ALERTS.md`)
- ✅ Historical data on filtering behavior
- ✅ Per-product granularity tracking
- ✅ Foundation for SLI/SLO monitoring

**Alerting:**
See `PROMETHEUS_ALERTS.md` for:
- Critical alert: `DailySnapshotsInBillableUsagePipeline`
- Warning alert: `HighRateOfNonHourlySnapshotFiltering`
- Grafana dashboard queries
- Incident response runbook

---

## Edge Cases Covered

### ✅ Both DAILY and HOURLY sent together
**Test:** `testOnlyHourlyGranularityIsProcessed()`
**Scenario:** Tally service sends both granularities
**Result:** Only HOURLY is processed

### ✅ Other granularities (YEARLY, MONTHLY, etc.)
**Test:** `shouldSkipNonDailySnapshots()` in BillableUsageMapperTest
**Scenario:** Non-HOURLY granularities are sent
**Result:** All non-HOURLY granularities are filtered

### ✅ No measurements in snapshot
**Test:** `shouldSkipSummaryWithNoMeasurements()` in BillableUsageMapperTest
**Scenario:** Snapshot has no tally measurements
**Result:** Snapshot is filtered regardless of granularity

---

## Test Execution

### Running Unit Tests
```bash
cd swatch-billable-usage
../mvnw test -Dtest=BillableUsageMapperTest
```

### Running Component Tests
```bash
cd component-tests
./mvnw test -Dtest=TallySummaryConsumerComponentTest#testOnlyHourlyGranularityIsProcessed
```

### Running All Billable Usage Tests
```bash
cd swatch-billable-usage
../mvnw test
```

---

## Acceptance Criteria Validation

### ✅ AC1: Ensure that there is a work component test plan around processing tally hourly summaries and NOT daily
**Status:** COMPLETE
**Evidence:** This document + `testOnlyHourlyGranularityIsProcessed()` component test

### ✅ AC2: This test plan should be presented to the team before the tests are merged in
**Status:** READY FOR REVIEW
**Evidence:** This document provides comprehensive test coverage documentation

### ✅ AC3: Should include a negative test of attempting to process a Daily Summary
**Status:** COMPLETE
**Evidence:**
- Component test sends DAILY summary and verifies it's NOT processed
- Unit test `shouldSkipDailyGranularitySnapshots()` validates filtering

### ✅ AC4: See if we can create a negative test to ensure if a daily snap is processed (and sent to the producers) we are notified
**Status:** COMPLETE ✅
**Evidence:**
- **Integration Test:** `TallySummaryMessageConsumerTest.testDailySnapshotBypassingFilterIsLogged()`
  - Creates a DAILY snapshot and simulates filtering failure
  - Verifies DAILY snapshot is sent to producers (the bug scenario)
  - Verifies producer logs INFO message containing the DAILY snapshot's tallyId
  - **This is the notification mechanism** - INFO logs visible in production allow tracing
- **Implementation:** Producer logs every billable usage message at INFO level
  - Format: `"Producing billable usage: tallyId={}, product={}, orgId={}, ..."`
  - Contains tallyId which links back to the original DAILY snapshot
  - Allows detection and investigation if DAILY bypasses filtering

---

## Risks & Mitigation

### Risk 1: Filter logic bypassed
**Mitigation:** Component test catches this before merge

### Risk 2: DAILY snapshots sent to production
**Mitigation:**
- Tests run in CI/CD before deployment
- Debug logs provide visibility
- (Recommended) Add production metrics for alerting

### Risk 3: Incorrect granularity mapping
**Mitigation:** Unit tests cover all granularity types

---

## Recommendations

1. **Short-term (Required):** Ensure all tests pass before merging SWATCH-3790
2. **Medium-term (Recommended):** Add production metrics for filtered snapshots
3. **Long-term (Optional):** Consider alerting if DAILY snapshots appear in production

---

## Summary

The billable usage service has comprehensive test coverage to ensure:
- ✅ Only HOURLY granularity tally summaries are processed
- ✅ DAILY summaries are filtered out and never reach producers
- ✅ Team is notified via test failures if filtering breaks
- ✅ Debug logs provide visibility into filtering behavior

**The existing `testOnlyHourlyGranularityIsProcessed()` component test is the primary negative test that ensures the team is notified if DAILY snapshots are processed and sent to producers.**