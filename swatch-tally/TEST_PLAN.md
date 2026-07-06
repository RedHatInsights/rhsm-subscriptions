# Introduction

The **swatch-tally** module is a core service within the Subscription Watch platform that processes usage events and produces aggregated tally snapshots for reporting and capacity management. It transforms individual instance metrics into time-based snapshots at various granularities (hourly, daily, weekly, monthly, quarterly, yearly).

This document outlines the test plan for swatch-tally, including event processing, snapshot generation, and report retrieval.

**Purpose:** To ensure the swatch-tally service is functional, reliable, and meets all defined requirements for usage tracking and reporting.

**Scope:**

* Event ingestion and processing
* Hourly and nightly tally snapshot generation
* Tally report generation with various filters
* Instance report generation
* Conflict handling and data persistence
* Idempotency of tally operations
* SLA and usage filtering

**Assumptions:**

* The swatch-tally service is deployed in a stable and functional environment
* Events are provided via Kafka topics in the expected format
* Database (PostgreSQL) is available and properly configured

**Constraints:**

* Testing is limited to the functionality of swatch-tally at a component level
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan

# Test Strategy

This test plan focuses on covering test scenarios for component-level tests utilizing the Java component test framework.

**Testing Strategy:**

Test cases should be testable locally and in deployed environments.

- Kafka messages can be injected for event-driven testing
- Database state can be seeded directly for specific scenarios
- System state can be verified through internal and public API calls
- Tally summary messages can be verified on Kafka topics

# Test Cases

## Tally Conflict Handling

**tally-conflicts-TC001 - Positive metric value updates**

- **Description**: Verify that tally replaces existing metric values when a new event with a higher positive value is received for the same instance and timestamp hour
- **Setup**:
    - Organization is opted in
    - Initial event with metric value 10.0 for a specific instance and hour
    - Hourly tally is performed
- **Action**:
    - Send updated event with metric value 25.0 for same instance and hour
    - Perform hourly tally
- **Verification**:
    - Initial tally sum equals 10.0
    - Updated tally sum equals 25.0
    - Tally reflects the most recent positive measurement value
- **Expected Result**:
    - Service updates the tally to reflect the new positive metric value
    - Previous value is replaced, not accumulated

**tally-conflicts-TC002 - Negative metric value rejection**

- **Description**: Verify that tally ignores updates when a negative metric value is received for the same instance and timestamp hour
- **Setup**:
    - Organization is opted in
    - Initial event with metric value 10.0 for a specific instance and hour
    - Hourly tally is performed
- **Action**:
    - Send event with negative metric value -25.0 for same instance and hour
    - Perform hourly tally
- **Verification**:
    - Initial tally sum equals 10.0
    - Tally sum after negative event still equals 10.0
    - Negative measurement is ignored
- **Expected Result**:
    - Service rejects negative metric values
    - Tally maintains the previous positive value

**tally-conflicts-TC003 - Multiple products same instance**

- **Description**: Verify that tally maintains separate counts for multiple products associated with the same instance ID
- **Setup**:
    - Organization is opted in
    - Events for same instance ID with different products (RHACM, ROSA)
    - Each product has multiple metrics
- **Action**:
    - Send one event per (product, metric) combination for the same instance
    - Perform hourly tally
- **Verification**:
    - Each product shows exactly 1 instance
    - Each product/metric combination has correct tally totals
    - Instance counts are per-product, not global
- **Expected Result**:
    - Service tallies metrics separately per product
    - Same instance can contribute to multiple product tallies

**tally-conflicts-TC004 - Multiple products conflicting events across hours**

- **Description**: Verify that tally deduplicates instances while aggregating metrics for multiple products across different hours
- **Setup**:
    - Organization is opted in
    - Events for same instance with different products in hour 1
    - Additional events for same products in hour 2
- **Action**:
    - Send events for RHACM and ROSA in first hour
    - Perform hourly tally
    - Send events for RHACM and ROSA in second hour
    - Perform hourly tally
- **Verification**:
    - Each product shows 1 unique instance across 2 hours
    - Tally totals are doubled (sum of both hours)
    - Instance count remains 1 per product
- **Expected Result**:
    - Service aggregates metrics across multiple hours
    - Instance deduplication works across time ranges

**tally-conflicts-TC005 - Counter backfill amends hourly totals**

- **Description**: Verify late events for earliest and latest hours in range add to hourly totals without removing intermediate snapshot values
- **Setup**:
    - Product OpenShift-dedicated-metrics, random billing account for events
    - Create 5 hourly events across 7-hour window with value V
- **Action**:
    - Run hourly tally; capture hourly report total for range
    - Add amendment event at oldest hour (value v1)
    - Add amendment event at newest hour (value v2)
    - Run hourly tally again
- **Verification**:
    - New hourly total = prior total + v1 + v2 (per metric under test)
- **Expected Result**:
    - Counter metric hourly snapshots are amended in-place; no regression on unaffected hours

**tally-conflicts-TC006 - Ansible counter amendments at two timestamps**

- **Description**: Verify two late Instance-hours (counter) events for ansible-aap-managed; one at an early hour and one at the latest hour — each add their value to the hourly report total without clearing intermediate snapshots
- **Setup**:
    - Product ansible-aap-managed, fixed billing account
    - 7-hour report window; baseline hourly total for Instance-hours already established (or start from zero)
- **Action**:
    - Capture baseline hourly Instance-hours total for the window
    - Publish amendment event at hour T-5: distinct instance_id, measurement Instance-hours = A
    - Publish amendment event at current hour: distinct instance_id, measurement Instance-hours = A
    - Run hourly tally again
- **Verification**:
    - New hourly Instance-hours total = prior total + (2 × A)
    - Intermediate hours in the window are not zeroed or removed
- **Expected Result**:
    - Counter amendments accumulate by event value across timestamps; swatch does not require a producer “cluster” or control/managed pair for this behavior

**tally-conflicts-TC007 - Shared instance_id product isolation**

- **Description**: Verify when a PAYG metered cluster and a traditional non-PAYG cloud host share the same instance_id, hourly PAYG tally is unchanged after nightly non-PAYG reconcile, and the non-PAYG host appears only on the expected traditional product with cloud category socket contribution
- **Setup**:
    - Organization is opted in
    - Fixed instance_id / instance_uuid
    - Fixed AWS billing_provider and billing_account_id for PAYG events
    - Step A — PAYG:
        - Publish mock PAYG cluster event for rhel-for-x86-els-payg with that instance_uuid, billing fields above, and a known vCPUs value
        - Run hourly tally for the PAYG product
        - Record PAYG instances report vCPUs for that instance
    - Step B — Non-PAYG:
        - Seed AWS public cloud non-PAYG host (RHEL for x86) with the same instance_id, no marketplace extra facts (aws_billing_products omitted)
        - Run nightly tally
- **Action**:
    - Query PAYG instances report for the PAYG product, filtered by instance_id / display name
    - Query non-PAYG instances/system table for the traditional product and host display name
- **Verification**:
    - PAYG row: vCPUs unchanged from Step A baseline
    - Non-PAYG row: present on expected traditional product only
    - Non-PAYG row: category=cloud, cloud_provider=aws, sockets=1
- **Expected Result**:
    - Shared instance identity does not cross-contaminate PAYG and traditional non-PAYG tally contributions; each product retains its own measurements after reconcile

## Product Tag and Metric Filtering

**tally-product-filter-TC001 - Mixed PAYG and TRADITIONAL tags filtered correctly**

- **Description**: Verify that hourly tally filters out non-PAYG (TRADITIONAL) product tags from events with mixed tags
- **Setup**:
    - Organization is opted in
    - Event with both PAYG tag (rhel-for-x86-els-payg-addon) and TRADITIONAL tag (rhel-for-x86-els-unconverted)
    - Event has vCPUs metric with value 4.0
- **Action**:
    - Send event with mixed product tags to service instance ingress topic
    - Perform hourly tally
- **Verification**:
    - PAYG product (rhel-for-x86-els-payg-addon) has tally sum of 4.0 for vCPUs
    - TRADITIONAL product (rhel-for-x86-els-unconverted) has NO hourly tally data
    - PAYG product appears in instances report with 1 instance
    - TRADITIONAL product has 0 instances in hourly instances report
- **Expected Result**:
    - Only PAYG product tags are processed during hourly tally
    - TRADITIONAL product tags are filtered out and not included in hourly billing

**tally-product-filter-TC002 - Event with only TRADITIONAL tags not tallied hourly**

- **Description**: Verify that events containing only TRADITIONAL product tags are not processed by hourly tally
- **Setup**:
    - Organization is opted in
    - Event with only TRADITIONAL tag (rhel-for-x86-els-unconverted)
    - Event has Sockets metric with value 2.0
- **Action**:
    - Send event with only TRADITIONAL tag
    - Perform hourly tally
- **Verification**:
    - TRADITIONAL product has NO hourly tally data (product_tag cleared to null)
    - TRADITIONAL product has 0 instances in hourly instances report
- **Expected Result**:
    - Events with only TRADITIONAL tags are excluded from hourly processing
    - product_tag field is cleared when no PAYG tags remain

**tally-product-filter-TC003 - Event with only PAYG tags processed normally**

- **Description**: Verify that events containing only PAYG product tags are processed normally without filtering
- **Setup**:
    - Organization is opted in
    - Event with only PAYG tag (rhel-for-x86-els-payg-addon)
    - Event has vCPUs metric with value 4.0
- **Action**:
    - Send event with only PAYG tag
    - Perform hourly tally
- **Verification**:
    - PAYG product has tally sum of 4.0 for vCPUs
    - PAYG product appears in instances report with 1 instance
- **Expected Result**:
    - PAYG-only events are processed normally by hourly tally
    - No filtering occurs when all tags are PAYG-eligible

**tally-product-filter-TC004 - Multiple events with mixed tags filtered correctly**

- **Description**: Verify that multiple events with different tag combinations are all filtered correctly
- **Setup**:
    - Organization is opted in
    - Event 1: Mixed tags (PAYG + TRADITIONAL) with vCPUs value 4.0
    - Event 2: Mixed tags (PAYG + TRADITIONAL) with vCPUs value 2.0, 30 minutes later
    - Event 3: PAYG only with vCPUs value 6.0, 45 minutes later (all in same hour)
- **Action**:
    - Send all three events
    - Perform hourly tally
- **Verification**:
    - PAYG product has tally sum of 6.0 (max value from latest event)
    - TRADITIONAL product has NO hourly tally data
- **Expected Result**:
    - All events have TRADITIONAL tags filtered out
    - PAYG product correctly aggregates using max value per hour
    - TRADITIONAL product excluded from all hourly processing

**tally-product-filter-TC005 - Conflict resolution with mixed PAYG and TRADITIONAL tags**

- **Description**: Verify that conflict resolution correctly handles events with mixed tags, updating PAYG measurements while keeping TRADITIONAL tags filtered out
- **Setup**:
    - Organization is opted in
    - Event 1: Mixed tags (PAYG + TRADITIONAL) with vCPUs value 4.0
    - Perform hourly tally (creates initial snapshot)
    - Event 2: Mixed tags (PAYG + TRADITIONAL) with vCPUs value 8.0 for same instance and hour (conflict)
    - Perform hourly tally again (triggers conflict resolution)
- **Action**:
    - Send first event with mixed tags
    - Perform hourly tally to create initial snapshot
    - Send second conflicting event with mixed tags and higher value
    - Perform hourly tally to trigger conflict resolution
- **Verification**:
    - After first tally: PAYG product has tally sum of 4.0
    - After second tally: PAYG product has tally sum of 8.0 (conflict resolved to higher value)
    - PAYG instance measurements updated to 8.0
    - TRADITIONAL product has 0 instances in both tally runs
- **Expected Result**:
    - Conflict resolution works correctly with filtered PAYG tags
    - PAYG product measurements are updated to the higher value
    - TRADITIONAL tags remain filtered out during conflict resolution
    - Instance data reflects the resolved measurement value

**tally-product-filter-TC006 - Mixed tags with single-metric edge case**

- **Description**: Verify mixed PAYG/TRADITIONAL tag events still process correctly when only PAYG metric data is present.
- **Setup**:
    - Organization is opted in
    - Event with PAYG + TRADITIONAL tags
    - Event includes only vCPUs metric value (no TRADITIONAL metric)
- **Action**:
    - Send mixed-tag single-metric event
    - Perform hourly tally
- **Verification**:
    - PAYG product has expected vCPUs tally
    - PAYG instance is present with expected measurement
    - TRADITIONAL product has 0 instances in hourly instances report
- **Expected Result**:
    - PAYG data is retained and tallied
    - TRADITIONAL tag remains filtered out, even with incomplete metric payload

**tally-product-filter-TC007 - Role-based product tag lookup filters non-PAYG metrics**

- **Description**: Verify that when product tag is derived from role (not explicitly provided), only metrics supported by the PAYG product are tallied and unsupported metrics are filtered out during normalization.
- **Setup**:
    - Organization is opted in
    - Event with NO product tag (empty product tag set)
    - Event with role "moa" which maps to PAYG product "rosa"
    - Event includes BOTH a PAYG-supported metric (Cores) and a TRADITIONAL metric (Sockets)
    - Cores metric value: 8.0
    - Sockets metric value: 2.0
    - Event has AWS billing provider and account ID
- **Action**:
    - Send event with empty product tag, role=moa, and mixed metrics to service instance ingress topic
    - Perform hourly tally
- **Verification**:
    - Product tag "rosa" is derived from role "moa" during event normalization
    - rosa product has tally sum of 8.0 for Cores metric
    - rosa instance appears in hourly instances report with Cores=8.0 and Instance-hours=0.0
    - Sockets metric is filtered out (not supported by rosa)
    - NO RHEL product instances are created (Sockets metric should not trigger RHEL product creation)
- **Expected Result**:
    - Role-based product tag derivation works correctly for PAYG products
    - Only metrics supported by the derived PAYG product are retained after normalization
    - Unsupported metrics (like Sockets for rosa) are filtered out and do not cause incorrect product tallies

**tally-product-filter-TC008 - Metrics filtering drops unknown metrics**

- **Description**: Verify events carrying a metric not defined in product configuration do not appear in hourly tally measurements
- **Setup**:
    - Product rosa, billing account scoped for events
    - Event A: all valid rosa metrics (Cores, Instance-hours) at value 10
    - Event B: valid metrics set at 20, invalid metric at 99
- **Action**:
    - Publish events to service instance ingress
    - Run hourly tally
    - Inspect hourly tally report and instances measurements for billing account
- **Verification**:
    - Valid metrics present with expected values
    - Invalid metric absent from all measurements / snapshots
- **Expected Result**:
    - Product-config-driven metric filtering drops unknown metrics at ingest/normalization

## Hypervisor Handling

**tally-hypervisor-TC001 - RHEL hypervisor without guests appears in instances report**

- **Description**: Verify that a RHEL-based hypervisor with no guests appears in the instances report when the hypervisor itself has RHEL usage data
- **Setup**:
    - Organization is opted in
    - Nightly tally is performed
    - RHEL hypervisor host is inserted with RHEL for x86 buckets but no guests
- **Action**:
    - Perform tally for organization
    - Retrieve instances report for RHEL for x86 product for the day
- **Verification**:
    - Hypervisor's subscription manager ID is in instances report data
    - Instances report includes the hypervisor
    - Hypervisor shows expected socket/core counts from its buckets
- **Expected Result**:
    - RHEL-based hypervisors appear in instances reports based on their own RHEL usage
    - Guest count is irrelevant when the hypervisor itself is running RHEL
    - Hypervisor is treated as a RHEL instance

**tally-hypervisor-TC002 - RHEL hypervisor without guests contributes to daily total**

- **Description**: Verify that a RHEL-based hypervisor with no guests contributes to the daily total socket count based on its own RHEL usage
- **Setup**:
    - Organization with baseline usage (non-zero sockets) for RHEL for x86
    - Nightly tally is performed to establish baseline
    - RHEL hypervisor host is inserted with RHEL for x86 buckets but no guests
- **Action**:
    - Capture initial daily sockets total for RHEL for x86
    - Perform tally for organization
    - Capture new daily sockets total for RHEL for x86
- **Verification**:
    - New sockets total is greater than initial sockets total
    - Difference equals the hypervisor's socket count from its buckets
    - Hypervisor contributed to the total
- **Expected Result**:
    - RHEL-based hypervisors contribute to tally totals based on their own RHEL usage
    - Guest count does not affect whether the hypervisor contributes to totals
    - Hypervisor usage is aggregated with other RHEL instances

**tally-hypervisor-TC003 - Non-RHEL hypervisor without usage data not in instances report**

- **Description**: Verify that a non-RHEL hypervisor without RHEL usage data does not appear in the RHEL instances report
- **Setup**:
    - Organization with baseline tally data for RHEL for x86
    - Nightly tally is performed
    - Non-RHEL hypervisor host (e.g., ESXi, Hyper-V) is inserted with no guests and no RHEL for x86 buckets
- **Action**:
    - Perform tally for organization
    - Retrieve instances report for RHEL for x86 product for the day
- **Verification**:
    - Hypervisor's subscription manager ID is not in instances report data
    - Instances report does not include the hypervisor
- **Expected Result**:
    - Non-RHEL hypervisors without RHEL buckets are excluded from RHEL instances reports
    - Only hosts with RHEL usage data appear in RHEL reports
    - Hypervisors not running RHEL (ESXi, Hyper-V) do not contribute to RHEL metrics

**tally-hypervisor-TC004 - Non-RHEL hypervisor without usage data does not affect daily total**

- **Description**: Verify that a non-RHEL hypervisor without RHEL usage data does not affect the daily total socket count for RHEL
- **Setup**:
    - Organization with baseline usage (non-zero sockets) for RHEL for x86
    - Nightly tally is performed to establish baseline
    - Non-RHEL hypervisor host (e.g., ESXi, Hyper-V) is inserted with no guests and no RHEL for x86 buckets
- **Action**:
    - Capture initial daily sockets total for RHEL for x86
    - Perform tally for organization
    - Capture new daily sockets total for RHEL for x86
- **Verification**:
    - Initial sockets total equals new sockets total
    - Hypervisor did not contribute to total
- **Expected Result**:
    - Non-RHEL hypervisors without RHEL buckets do not affect RHEL tally totals
    - Only hosts with RHEL buckets contribute to aggregated RHEL metrics
    - Hypervisor platform type does not create RHEL usage where none exists

**tally-hypervisor-TC005 - RHEL hypervisor with guests increases total sockets**

- **Description**: Verify hypervisor with guest mapping increases hypervisor socket totals and hypervisor appears in instances report
- **Setup**:
    - Capture baseline daily tally (all metrics) for RHEL for x86
    - Create hypervisor (1 socket) + 2 guests
- **Action**:
    - Sync nightly tally
    - Query instances report category hypervisor filtered by hypervisor display name
- **Verification**:
    - hypervisor_sockets increases by at least 1
    - Total sockets increases by at least 1
    - cloud_sockets / cloud_cores unchanged
    - Hypervisor row in instances; all rows category hypervisor
    - Guest count on hypervisor = 2
- **Expected Result**:
    - Hypervisor topology reflected in tally totals and instances category filter

**tally-hypervisor-TC006 - RHEL hypervisor updates guest mapping**

- **Description**: Verify remapping guest from hypervisor A to hypervisor B updates instances presence
- **Setup**:
    - Hypervisor A + 1 guest (synced, instance present)
    - Hypervisor B with 0 guests (instance not present initially)
- **Action**:
    - Move guest from A to B (update hypervisor mapping)
    - Sync nightly tally
- **Verification**:
    - Hypervisor A no longer in system table as a present instance
    - Hypervisor B present with 1 guest
- **Expected Result**:
    - Guest mapping updates change hypervisor visibility in tally inventory

## Data Persistence

**tally-persistence-TC001 - Tally report is idempotent across separate tally runs**

- **Description**: Verify that tally reports remain unchanged when hourly tally is re-run for the same time period
- **Setup**:
    - Organization is opted in
    - Events for yesterday and today are created
    - Initial hourly tally is performed
- **Action**:
    - Capture tally reports for today and yesterday
    - Re-run hourly tally
    - Capture tally reports again
- **Verification**:
    - Today's tally before equals today's tally after
    - Yesterday's tally before equals yesterday's tally after
    - No changes occur on re-tally
- **Expected Result**:
    - Tally reports are idempotent
    - Re-running tally does not modify previously calculated data

**tally-persistence-TC002 - Instance report is idempotent across separate tally runs**

- **Description**: Verify that instance reports remain unchanged when hourly tally is re-run for the same time period
- **Setup**:
    - Organization is opted in
    - Events for yesterday are created
    - Initial hourly tally is performed
- **Action**:
    - Capture instance report's last applied event date for yesterday
    - Re-run hourly tally
    - Capture instance report's last applied event date again
- **Verification**:
    - Last applied event date before re-tally equals date after re-tally
    - Instance data remains stable
- **Expected Result**:
    - Instance reports are idempotent
    - Re-running tally does not modify instance metadata

**tally-persistence-TC003 - Previous-month backfill leaves current month unchanged**

- **Description**: Verify hourly tally over a previous-month range includes late-added events for that month without changing current-month daily totals
- **Setup**:
    - Product OpenShift-dedicated-metrics used for events
    - Capture current-month daily total for metric
    - Capture previous-month daily total for days 1–3 of prior month
    - Create three PAYG events dated in previous month (days 1–3)
- **Action**:
    - Run hourly tally scoped to previous-month range
- **Verification**:
    - Previous-month daily sum increases by 3 (one per event)
    - Current-month daily sum unchanged before vs after
- **Expected Result**:
    - Month-scoped hourly processing backfills historical snapshots without cross-month contamination

## Report Granularity and Filtering (PAYG)

### Test Organization

The PAYG tally report filtering test cases are organized into two component test files:

- **TallyReportFiltersPaygTest.java** - Contains 23 PAYG test cases (`tally-report-filters-payg-TC***`; TC009, TC010, TC023 live in EdgeCaseTest):
  - TC001-TC008: Basic filtering by granularity, SLA, usage, billing provider, and billing account ID
  - TC011-TC014: Validation errors and metadata verification
  - TC015-TC022: Unfiltered aggregation, daily filtering after nightly tally, monthly/quarterly/yearly granularity
  - TC024: Invalid granularity enum value
  - TC025: Daily report totals equal sum of hourly values
  - Product: RHEL for x86 ELS PAYG (supports hourly granularity)

- **TallyReportFiltersEdgeCaseTest.java** - Contains 3 PAYG edge cases requiring special event patterns:
  - TC009: Multiple events aggregation with same filter attributes
  - TC010: Three distinct SLA values filtering
  - TC023: Billing account change for same instance
  - Product: RHEL for x86 ELS PAYG (supports hourly granularity)

All test files use `@BeforeAll` to create test data once and share it across all test methods. Each test is parameterized with `@ValueSource(booleans = {true, false})` to verify behavior with both the legacy query path and the primary row searches feature flag enabled.

**tally-report-filters-payg-TC001 - Daily granularity with all filters**

- **Description**: Verify that tally report API returns daily granularity data with all filter parameters
- **Setup**:
    - Organization is opted in
    - Daily time range is specified (3 days ago to 2 days ago)
- **Action**:
    - Request tally report with granularity=Daily, SLA, usage, billing provider, and random billing account ID
- **Verification**:
    - Response data is not null
    - Response metadata matches requested filters
    - Metadata granularity is DAILY
    - Metadata includes product, metric, SLA, usage, billing provider, and billing account ID
    - Data count matches metadata count
- **Expected Result**:
    - API returns daily tally data with all specified filters
    - Metadata accurately reflects the request parameters

**tally-report-filters-payg-TC002 - Hourly granularity filtered by SLA**

- **Description**: Verify that tally report API filters data by SLA parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with SLA=PREMIUM (value 10.0)
    - Event 2 created with SLA=STANDARD (value 20.0)
    - Hourly tally is performed
    - Hourly time range is specified (2 hours ago)
- **Action**:
    - Request tally report with granularity=Hourly and sla=STANDARD
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes SLA=STANDARD
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by SLA parameter
    - Only data matching the specified SLA is returned

**tally-report-filters-payg-TC003 - Hourly granularity filtered by usage**

- **Description**: Verify that tally report API filters data by usage parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with usage=PRODUCTION (value 10.0)
    - Event 2 created with usage=DEVELOPMENT (value 20.0)
    - Hourly tally is performed
    - Hourly time range is specified (2 hours ago)
- **Action**:
    - Request tally report with granularity=Hourly and usage=PRODUCTION
- **Verification**:
    - Response data contains only Event 1's data (value 10.0)
    - Response metadata includes usage=PRODUCTION
    - Response does not include Event 2's data
- **Expected Result**:
    - API filters tally data by usage parameter
    - Only data matching the specified usage is returned

**tally-report-filters-payg-TC004 - Hourly granularity filtered by billing provider**

- **Description**: Verify that tally report API filters data by billing provider parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_provider=AWS (value 10.0)
    - Event 2 created with billing_provider=AZURE (value 20.0)
    - Hourly tally is performed
    - Hourly time range is specified (2 hours ago)
- **Action**:
    - Request tally report with granularity=Hourly and billing_provider=AZURE
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes billing_provider=AZURE
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by billing provider parameter
    - Only data matching the specified billing provider is returned

**tally-report-filters-payg-TC005 - Hourly granularity filtered by billing account ID**

- **Description**: Verify that tally report API filters data by billing account ID parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_account_id=account-123 (value 10.0)
    - Event 2 created with billing_account_id=account-456 (value 20.0)
    - Hourly tally is performed
    - Hourly time range is specified (2 hours ago)
- **Action**:
    - Request tally report with granularity=Hourly and billing_account_id=account-456
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes billing_account_id=account-456
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by billing account ID parameter
    - Only data matching the specified billing account ID is returned

**tally-report-filters-payg-TC006 - Daily granularity with partial filters**

- **Description**: Verify that tally report API returns daily granularity data with only some filter parameters
- **Setup**:
    - Organization is opted in
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity=Daily, SLA, and usage only
- **Verification**:
    - Response data is not null
    - Response metadata includes SLA and usage
    - Metadata does not include billing provider or billing account ID
    - Metadata granularity is DAILY
- **Expected Result**:
    - API accepts partial filter sets
    - Unspecified filters are not present in metadata

**tally-report-filters-payg-TC007 - Hourly granularity with all filters**

- **Description**: Verify that tally report API returns hourly granularity data with all filter parameters
- **Setup**:
    - Organization is opted in
    - Hourly time range is specified (4 hours ago to 1 hour from now)
- **Action**:
    - Request tally report with granularity=Hourly and all filters (SLA, usage, billing provider, random billing account ID)
- **Verification**:
    - Response data is not null
    - Metadata granularity is HOURLY
    - All filter parameters are reflected in metadata
- **Expected Result**:
    - API returns hourly tally data
    - Hourly granularity data includes all applied filters

**tally-report-filters-payg-TC008 - Invalid request without granularity**

- **Description**: Verify that tally report API returns validation error when granularity parameter is missing
- **Setup**:
    - Organization is opted in
    - Time range is specified
- **Action**:
    - Request tally report without granularity parameter
- **Verification**:
    - Response status code is 400 (Bad Request)
    - Response body contains "granularity: must not be null"
- **Expected Result**:
    - API validates required parameters
    - Appropriate error message is returned

**tally-report-filters-payg-TC009 - Multiple events with same filter values are aggregated**

- **Description**: Verify that multiple events with identical filter attributes are properly aggregated in tally reports
- **Setup**:
    - Organization is opted in
    - Three events created with SLA=PREMIUM (values 15.0, 25.0, 10.0)
    - All events have same timestamp hour
    - Hourly tally is performed
- **Action**:
    - Request tally report with granularity=Hourly and sla=PREMIUM filter
- **Verification**:
    - Response data contains aggregated value of 50.0 (15+25+10)
    - Response metadata includes SLA=PREMIUM
    - All three events are summed correctly
- **Expected Result**:
    - API correctly aggregates multiple events with same filter values
    - Total reflects sum of all matching events

**tally-report-filters-payg-TC010 - Three distinct filter value combinations**

- **Description**: Verify that filtering works correctly when three different SLA values exist in the same hour
- **Setup**:
    - Organization is opted in
    - Event 1 created with SLA=PREMIUM (value 10.0)
    - Event 2 created with SLA=STANDARD (value 20.0)
    - Event 3 created with SLA=SELF_SUPPORT (value 30.0)
    - All events have same timestamp hour
    - Hourly tally is performed
- **Action**:
    - Request tally report with granularity=Hourly and sla=SELF_SUPPORT filter
- **Verification**:
    - Response data contains only Event 3's data (value 30.0)
    - Response metadata includes SLA=SELF_SUPPORT
    - Response does not include Event 1 or Event 2 data
- **Expected Result**:
    - API correctly isolates data by filter value
    - Only matching SLA data is returned when multiple SLA values exist

**tally-report-filters-payg-TC011 - Invalid request without beginning timestamp**

- **Description**: Verify that tally report API returns validation error when beginning parameter is missing
- **Setup**:
    - Organization is opted in
    - Ending timestamp is specified
- **Action**:
    - Request tally report with granularity and ending, but without beginning parameter
- **Verification**:
    - Response status code is 400 (Bad Request)
    - Response body contains "beginning" or "must not be null"
- **Expected Result**:
    - API validates required beginning parameter
    - Appropriate error message is returned

**tally-report-filters-payg-TC012 - Invalid request without ending timestamp**

- **Description**: Verify that tally report API returns validation error when ending parameter is missing
- **Setup**:
    - Organization is opted in
    - Beginning timestamp is specified
- **Action**:
    - Request tally report with granularity and beginning, but without ending parameter
- **Verification**:
    - Response status code is 400 (Bad Request)
    - Response body contains "ending" or "must not be null"
- **Expected Result**:
    - API validates required ending parameter
    - Appropriate error message is returned

**tally-report-filters-payg-TC013 - Metadata reflects no filters when omitted**

- **Description**: Verify that response metadata correctly shows null values for optional filters when they are not provided
- **Setup**:
    - Organization is opted in
    - Daily time range is specified
- **Action**:
    - Request tally report with only required parameters (granularity, beginning, ending)
- **Verification**:
    - Response metadata has null values for serviceLevel, usage, billingProvider, billingAccountId
    - Required fields (granularity, product, metricId) are properly set
    - Metadata structure is valid
- **Expected Result**:
    - API properly differentiates between filtered and unfiltered requests
    - Null values indicate no filter was applied

**tally-report-filters-payg-TC014 - Metadata with EMPTY filter value**

- **Description**: Verify that response metadata correctly reflects EMPTY enum filter values
- **Setup**:
    - Organization is opted in
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity, time range, and sla=EMPTY
- **Verification**:
    - Response metadata serviceLevel equals EMPTY
    - Other unspecified filters remain null
    - EMPTY value is distinguished from null/unset
- **Expected Result**:
    - API properly handles EMPTY enum values in filters
    - EMPTY is treated as a valid filter value distinct from null

**tally-report-filters-payg-TC015 - All data returned when no optional filters applied**

- **Description**: Verify that tally report API returns all event data aggregated when querying without optional filter parameters
- **Setup**:
    - Organization is opted in
    - Event 1 created with SLA=PREMIUM, usage=PRODUCTION, billing_provider=AWS (value 10.0)
    - Event 2 created with SLA=STANDARD, usage=DEVELOPMENT, billing_provider=AZURE (value 20.0)
    - Event 3 created with SLA=SELF_SUPPORT, usage=PRODUCTION, billing_provider=AWS (value 30.0)
    - All events have same timestamp hour
    - Hourly tally is performed
- **Action**:
    - Request tally report with only required parameters (granularity, beginning, ending) and no optional filters
- **Verification**:
    - Response data contains aggregated value of 60.0 (10+20+30)
    - Response metadata has null values for serviceLevel, usage, billingProvider
    - All events are summed regardless of their filter attributes
- **Expected Result**:
    - API returns complete dataset when no filters are applied
    - Total reflects sum of all events regardless of SLA, usage, or billing attributes
    - Metadata correctly indicates no filters were applied (null values)

**tally-report-filters-payg-TC016 - Daily granularity filtered by SLA after nightly tally**

- **Description**: Verify that tally report API filters daily snapshots by SLA after running hourly tally followed by nightly tally
- **Setup**:
    - Organization is opted in
    - Event 1 created with SLA=PREMIUM (value 10.0)
    - Event 2 created with SLA=STANDARD (value 20.0)
    - Events timestamped yesterday
    - Hourly tally is performed and confirmed via Kafka messages
    - Nightly tally is performed to create daily snapshots
- **Action**:
    - Request tally report with granularity=Daily and sla=STANDARD
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes SLA=STANDARD
    - Response metadata includes granularity=DAILY
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters daily snapshot data by SLA parameter
    - Only data matching the specified SLA is returned from daily aggregation

**tally-report-filters-payg-TC017 - Daily granularity filtered by usage after nightly tally**

- **Description**: Verify that tally report API filters daily snapshots by usage after running hourly tally followed by nightly tally
- **Setup**:
    - Organization is opted in
    - Event 1 created with usage=PRODUCTION (value 10.0)
    - Event 2 created with usage=DEVELOPMENT (value 20.0)
    - Events timestamped yesterday
    - Hourly tally is performed and confirmed via Kafka messages
    - Nightly tally is performed to create daily snapshots
- **Action**:
    - Request tally report with granularity=Daily and usage=PRODUCTION
- **Verification**:
    - Response data contains only Event 1's data (value 10.0)
    - Response metadata includes usage=PRODUCTION
    - Response metadata includes granularity=DAILY
    - Response does not include Event 2's data
- **Expected Result**:
    - API filters daily snapshot data by usage parameter
    - Only data matching the specified usage is returned from daily aggregation

**tally-report-filters-payg-TC018 - Daily granularity filtered by billing provider after nightly tally**

- **Description**: Verify that tally report API filters daily snapshots by billing provider after running hourly tally followed by nightly tally
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_provider=AWS (value 10.0)
    - Event 2 created with billing_provider=AZURE (value 20.0)
    - Events timestamped yesterday
    - Hourly tally is performed and confirmed via Kafka messages
    - Nightly tally is performed to create daily snapshots
- **Action**:
    - Request tally report with granularity=Daily and billing_provider=AZURE
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes billing_provider=AZURE
    - Response metadata includes granularity=DAILY
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters daily snapshot data by billing provider parameter
    - Only data matching the specified billing provider is returned from daily aggregation

**tally-report-filters-payg-TC019 - Daily granularity filtered by billing account ID after nightly tally**

- **Description**: Verify that tally report API filters daily snapshots by billing account ID after running hourly tally followed by nightly tally
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_account_id=daily-account-123 (value 10.0)
    - Event 2 created with billing_account_id=daily-account-456 (value 20.0)
    - Events timestamped yesterday
    - Hourly tally is performed and confirmed via Kafka messages
    - Nightly tally is performed to create daily snapshots
- **Action**:
    - Request tally report with granularity=Daily and billing_account_id=daily-account-456
- **Verification**:
    - Response data contains only Event 2's data (value 20.0)
    - Response metadata includes billing_account_id=daily-account-456
    - Response metadata includes granularity=DAILY
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters daily snapshot data by billing account ID parameter
    - Only data matching the specified billing account ID is returned from daily aggregation

**tally-report-filters-payg-TC020 - Monthly granularity with all filters**

- **Description**: Verify that tally report API accepts monthly granularity and returns correct metadata with all filter parameters
- **Setup**:
    - Organization is opted in
    - Query parameters with granularity=Monthly and all available filters (sla, usage, billing_provider, billing_account_id)
- **Action**:
    - Request tally report with granularity=Monthly
- **Verification**:
    - Response metadata includes granularity=MONTHLY
    - Response metadata includes all specified filters (sla, usage, billing_provider, billing_account_id)
    - Response metadata includes product tag and metric ID
    - Response data size matches metadata count
- **Expected Result**:
    - API accepts and processes monthly granularity queries
    - All filter parameters are properly reflected in response metadata

**tally-report-filters-payg-TC021 - Quarterly granularity with all filters**

- **Description**: Verify that tally report API accepts quarterly granularity and returns correct metadata with all filter parameters
- **Setup**:
    - Organization is opted in
    - Query parameters with granularity=Quarterly and all available filters (sla, usage, billing_provider, billing_account_id)
- **Action**:
    - Request tally report with granularity=Quarterly
- **Verification**:
    - Response metadata includes granularity=QUARTERLY
    - Response metadata includes all specified filters (sla, usage, billing_provider, billing_account_id)
    - Response metadata includes product tag and metric ID
    - Response data size matches metadata count
- **Expected Result**:
    - API accepts and processes quarterly granularity queries
    - All filter parameters are properly reflected in response metadata

**tally-report-filters-payg-TC022 - Yearly granularity with all filters**

- **Description**: Verify that tally report API accepts yearly granularity and returns correct metadata with all filter parameters
- **Setup**:
    - Organization is opted in
    - Query parameters with granularity=Yearly and all available filters (sla, usage, billing_provider, billing_account_id)
- **Action**:
    - Request tally report with granularity=Yearly
- **Verification**:
    - Response metadata includes granularity=YEARLY
    - Response metadata includes all specified filters (sla, usage, billing_provider, billing_account_id)
    - Response metadata includes product tag and metric ID
    - Response data size matches metadata count
- **Expected Result**:
    - API accepts and processes yearly granularity queries
    - All filter parameters are properly reflected in response metadata

**tally-report-filters-payg-TC023 - Daily report tracks billing account change for same instance**

- **Description**: Verify that daily tally reports correctly track measurements when a single instance changes its billing account ID
- **Setup**:
    - Organization is opted in
    - Single instance identified by instanceId
    - First event published with billing_account_id=839214756108 (value 5.0) at T-2 hours
    - Second event published with billing_account_id=472061583927 (value 8.0) at T-1 hour for same instance
    - Events timestamped within the same day
    - Hourly tally is performed after each event
- **Action**:
    - Request daily tally report filtered by billing_account_id=839214756108
    - Request daily tally report filtered by billing_account_id=472061583927
    - Request daily tally report with no billing account filter
- **Verification**:
    - Report for billing_account_id=839214756108 shows value 5.0
    - Report for billing_account_id=472061583927 shows value 8.0
    - Report with no filter shows total value 13.0 (5.0 + 8.0)
- **Expected Result**:
    - Daily reports correctly attribute measurements to respective billing accounts when an instance changes billing account ID
    - Total aggregated value equals the sum of individual billing account values

**tally-report-filters-payg-TC024 - Invalid granularity enum value**

- **Description**: Verify that tally report API returns validation error when granularity is not a valid enum value
- **Setup**:
    - Organization is opted in
- **Action**:
    - Request tally report with granularity=HOURLLY (typo)
- **Verification**:
    - HTTP 400 Bad Request
- **Expected Result**:
    - Invalid enum query parameters return client error instead of server error

**tally-report-filters-payg-TC025 - Daily report totals sum of hourly values in same time window**

- **Description**: Verify for PAYG that sum of hourly report values (has_data hours) for yesterday equals daily report value for same window
- **Setup**:
    - Reset org
    - Two PAYG events yesterday (hours 07:00 and 10:00 UTC) + one event today (all with cores + instance-hours)
- **Action**:
    - Run hourly tally
    - Query hourly cores and instance-hours for yesterday’s beginning/ending
    - Query daily cores and instance-hours for same window
- **Verification**:
    - Report Sum(hourly values where has_data) = daily data point value for cores
    - Same for instance-hours
- **Expected Result**:
    - Daily snapshot equals aggregation of hourly snapshots for the period

## Report Has Data Based on Category (PAYG)

**tally-report-has-data-payg-TC001 - has_data matches category contribution**

- **Description**: Verify that hourly tally reports with a category filter set has_data from that category’s measurements only, not from snapshot presence or other hardware categories.
- **Setup**:
    - Organization is opted in
    - Cloud payg event published at hour T−2 (relative to current UTC hour) with vCPUs=8.0, sla=Premium
    - No event at gap hour T−4; no events at other hours in the queried range except T−2
    - Hourly tally is performed until category=cloud at T−2 shows value>0 and has_data=true
- **Action**:
    - Request hourly tally report for product tag and vCPUs metric with category set to each of physical, virtual, hypervisor, and cloud over range T−6 through end of T−2
    - Request category=cloud report and inspect gap hour T−4 and event hour T−2
    - Request physical, virtual, and hypervisor reports at event hour T−2
- **Verification**:
    - For every category in the range, no data point has value=0 and has_data=true unless that category contributed measurements
    - At gap hour T−4, category=cloud has value=0 and has_data=false
    - At event hour T−2, category=cloud has value=8 and has_data=true
    - At event hour T−2, physical, virtual, and hypervisor each have value=0 and has_data=false
- **Expected Result**:
    - has_data reflects category-specific measurement presence per bucket
    - Gap-filled hours report has_data=false even when value=0
    - Non-contributing categories do not report has_data=true when a cloud-only snapshot exists

**tally-report-has-data-payg-TC002 - Zero-value category measurements still report has_data**

- **Description**: Verify that a zero-quantity measurement for a contributing category still returns has_data=true with value=0 and that other categories at the same hour remain has_data=false when only cloud contributed.
- **Setup**:
    - Organization is opted in
    - Cloud payg event published at hour T−6 (relative to current UTC hour) with vCPUs=0.0, sla=Premium
    - Hourly tally is performed until category=cloud at T−6 shows value=0 and has_data=true
- **Action**:
    - Request hourly tally report for product tag and vCPUs metric with category=cloud for hour T−6 only
    - Request hourly reports with category physical, virtual, and hypervisor for the same hour
- **Verification**:
    - At event hour T−6, category=cloud has value=0 and has_data=true
    - At event hour T−6, physical, virtual, and hypervisor each have value=0 and has_data=false
- **Expected Result**:
    - Existing zero-value measurements for the filtered category are treated as present (has_data=true)
    - Categories that did not contribute at that hour still report has_data=false with value=0

**tally-report-has-data-payg-TC003 - Data gaps indicated by hasData field**

- **Description**: Verify that an unfiltered hourly tally report sets has_data per bucket.
- **Setup**:
    - Organization is opted in
    - Premium payg events created at hour 0 (value 10.0) and hour 2 (value 20.0)
    - Four-hour range where hours 1 and 3 have no events
    - Hourly tally is performed
- **Action**:
    - Request unfiltered hourly tally report for the 4-hour range (no category filter)
- **Verification**:
    - Response data contains data points for each hour in the range
    - Hour 0: value=10, has_data=true
    - Hour 2: value=20, has_data=true
    - Gap hours 1 and 3: value=0, has_data=false
    - No data point in the range has value=0 and has_data=true
- **Expected Result**:
    - Event hours report has_data=true with the expected tallied values
    - Gap-filled hours without a snapshot for that period report value=0 and has_data=false

## Report Granularity and Filtering (Non-PAYG)

Shared **non-PAYG physical RHEL fixture** (TC001–TC006): product RHEL for x86, category=physical, three nightly-tallied hosts — Host A: 4 sockets / Premium / Production; Host B: 6 sockets / Standard / Development/Test; Host C: 2 sockets / Premium / Development/Test; **total 12 sockets**. CT: seed via shared fixture helper.

**tally-report-filters-nonpayg-TC001 - Physical RHEL daily report SLA filter returns correct socket totals**

- **Description**: Verify daily tally report SLA filters return correct socket totals for non-PAYG physical RHEL
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query daily report, metric Sockets, category=physical, sla=Premium
    - Query with sla=Standard
- **Verification**:
    - Premium = 6 (Hosts A + C)
    - Standard = 6 (Host B)
- **Expected Result**:
    - Daily SLA filters match host totals

**tally-report-filters-nonpayg-TC002 - Physical RHEL daily report usage filter returns correct socket totals**

- **Description**: Verify daily usage filters return correct socket totals
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query usage=Production, category=physical
    - Query usage=Development/Test, category=physical
- **Verification**:
    - Production = 4 (Host A)
    - Development/Test = 8 (Hosts B + C)
- **Expected Result**:
    - Daily usage filters match host totals

**tally-report-filters-nonpayg-TC003 - Physical RHEL daily report combined SLA and usage filters return per-host totals**

- **Description**: Verify combined SLA and usage filters return per-host socket totals; invalid combo returns zero; response metadata echoes applied filters
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query Premium + Production; Standard + Development/Test; Premium + Development/Test; Standard + Production (category=physical)
    - For Premium + Production, query full report object (not value-only)
- **Verification**:
    - Premium + Production = 4 (Host A)
    - Standard + Development/Test = 6 (Host B)
    - Premium + Development/Test = 2 (Host C)
    - Standard + Production = 0 (no matching host)
    - Premium + Production report metadata includes SLA=Premium and usage=Production
    - Metadata granularity is DAILY; billing provider and billing account ID are null (not applied)
- **Expected Result**:
    - Combined daily filters match per-host values; metadata documents applied SLA and usage filters

**tally-report-filters-nonpayg-TC004 - Physical RHEL unfiltered daily report aggregates hosts; SLA slices partition total**

- **Description**: Verify unfiltered daily total and that Premium + Standard equals unfiltered total
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query without SLA/usage filters, category=physical
    - Query Premium and Standard separately
- **Verification**:
    - Unfiltered = 12
    - Premium = 6, Standard = 6
    - Premium + Standard = unfiltered
- **Expected Result**:
    - Unfiltered daily report aggregates all hosts; SLA slices partition total

**tally-report-filters-nonpayg-TC005 - Physical non-PAYG daily report excludes AWS billing provider filter**

- **Description**: Verify billing_provider=aws on non-PAYG daily report returns zero sockets (traditional snapshots use billing_provider=_ANY)
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query unfiltered, category=physical
    - Query with billing_provider=aws, category=physical
- **Verification**:
    - Unfiltered = 12
    - billing_provider=aws, filtered = 0
- **Expected Result**:
    - AWS billing provider filter excludes traditional non-PAYG nightly snapshots

**tally-report-filters-nonpayg-TC006 - Physical non-PAYG daily report excludes non-matching billing account ID**

- **Description**: Verify arbitrary billing account ID returns zero on non-PAYG daily report
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total; hosts have no billing_account_id)
- **Action**:
    - Run nightly tally
    - Query unfiltered, category=physical
    - Query with billing_account_id=test123, category=physical
- **Verification**:
    - Unfiltered = 12
    - billing_account_id=test123, filtered = 0
- **Expected Result**:
    - Billing account ID filter yields no matching nightly snapshot rows

**tally-report-filters-nonpayg-TC007 - Virtual RHEL daily report increments sockets and cores for matching SLA and usage**

- **Description**: Verify adding a virtual RHEL host increases daily tally report values under category=virtual for matching SLA and usage
- **Setup**:
    - Organization is opted in
    - Baseline daily virtual report for chosen SLA/usage (e.g. Premium + Production)
    - One virtual host: VIRTUAL buckets, 2 raw sockets normalized to 1 in tally, known cores value; SLA/usage match filter under test
- **Action**:
    - Run nightly tally
    - Query daily report for Sockets and Cores with same SLA, usage, and category=virtual
- **Verification**:
    - Sockets report value increases by 1 from baseline
    - Cores report value increases by expected increment (virtual guest cores rule)
    - Filtered SLA/usage totals unchanged for non-matching filters
- **Expected Result**:
    - Virtual category daily report filters track new virtual host socket and core contributions

**tally-report-filters-nonpayg-TC008 - Virtual RHEL empty-SLA socket partition equals all minus defined SLAs**

- **Description**: Verify empty-SLA virtual socket total equals all minus (Premium + Standard + Self-Support)
- **Setup**:
    - Organization is opted in
    - Three virtual hosts: Premium, Standard, and Self-Support SLAs (each normalized to 1 socket under category=virtual)
- **Action**:
    - Run nightly tally
    - Query daily Sockets report with category=virtual for each SLA, empty SLA (sla=""), and unfiltered (no SLA param)
- **Verification**:
    - Per-SLA socket values are each > 0
    - empty_sla.sockets = all.sockets - (premium + standard + self_support)
    - all.sockets = filtered_sum + empty_sla.sockets
- **Expected Result**:
    - Virtual SLA partition including empty SLA is consistent on daily report

**tally-report-filters-nonpayg-TC009 - category=virtual daily report excludes physical contributions**

- **Description**: Verify category=virtual filter on daily tally report returns only virtual socket totals and does not include physical or hypervisor hosts in the same org
- **Setup**:
    - Organization is opted in
    - One physical host (e.g. 4 sockets, Premium, Production) and one virtual host (e.g. 1 normalized socket) in same org
- **Action**:
    - Run nightly tally
    - Query daily Sockets for category=physical, category=virtual, and unfiltered (no category)
- **Verification**:
    - category=virtual value equals virtual host contribution only
    - category=physical value equals physical host contribution only
    - Unfiltered total equals sum of category contributions present in org
- **Expected Result**:
    - Daily report category filter isolates virtual measurements from physical (and other) hardware types

**tally-report-filters-nonpayg-TC010 - Non-marketplace AWS cloud host increases daily category=cloud report totals**

- **Description**: Verify adding a non-marketplace AWS public cloud host increases unfiltered daily category=cloud totals for sockets, cores, and instance_count
- **Setup**:
    - Organization is opted in
    - Capture baseline daily cloud report for RHEL for x86, category=cloud, today's window (record sockets, cores, instance_count)
    - Seed one AWS public cloud host for RHEL for x86:
        - number_of_sockets=2, cores_per_socket=2 (4 raw cores)
        - is_virtual=true, cloud_provider=aws
        - No marketplace extra facts (no aws_billing_products)
- **Action**:
    - Run nightly tally
    - Query daily tally report with metrics filter (Cores anchor), category=cloud, same window as baseline
- **Verification**:
    - sockets increased by +1 vs baseline (public cloud normalizes to 1 socket regardless of raw count)
    - instance_count increased by +1 vs baseline
    - cores increased by +2 vs baseline (ceil(4 / 2) with CT threads_per_core=2.0)
- **Expected Result**:
    - Unfiltered daily category=cloud report reflects new non-marketplace public cloud host

## Report Has Data Based on Category (Non-PAYG)

**tally-report-has-data-nonpayg-TC001 - Physical-only org daily has_data matches category contribution**

- **Description**: Verify has_data is never true when a category contributed zero sockets; physical buckets with positive sockets have has_data=true; virtual/hypervisor/cloud stay has_data=false when empty
- **Setup**:
    - Organization is opted in
    - Three hosts: physical total = 12 sockets; virtual/hypervisor/cloud remain empty
- **Action**:
    - Run nightly tally; 12-day window ending today
    - For each category in (physical, virtual, hypervisor, cloud), fetch daily sockets series over window
    - Compare latest physical bucket to system-table physical socket sum
    - Fetch narrow virtual window (yesterday–today)
- **Verification**:
    - No data point has value=0 and has_data=true in any category over wide window
    - Latest physical: has_data=true, value=12
    - Any physical point with value>0 should have has_data=true
    - Latest virtual: value=0, has_data not true
- **Expected Result**:
    - has_data truthfully indicates category-specific presence for daily non-PAYG reports

**tally-report-has-data-nonpayg-TC002 - Virtual category has_data true when virtual sockets contribute**

- **Description**: Verify daily tally report with category=virtual reports has_data=true when virtual socket contribution is present
- **Setup**:
    - Organization is opted in
    - At least one virtual RHEL host with VIRTUAL buckets and sockets > 0 after nightly tally
- **Action**:
    - Run nightly tally
    - Query daily Sockets report with category=virtual for a window including today
- **Verification**:
    - Latest (or today) data point has value > 0 and has_data=true
    - No data point in range has value=0 and has_data=true unless that day had no virtual contribution
- **Expected Result**:
    - has_data reflects virtual category measurement presence on daily report

**tally-report-has-data-nonpayg-TC003 - Mixed physical and virtual org: has_data per category only where category contributes**

- **Description**: Verify in an org with both physical and virtual hosts, each contributing category reports has_data=true with value > 0 on the same day; complements single-category cases in TC001 and TC002
- **Setup**:
    - Organization is opted in
    - One physical host (sockets > 0) and one virtual host (normalized sockets > 0) after nightly tally
- **Action**:
    - Run nightly tally
    - Query daily Sockets for category=physical and category=virtual for the same day/window
- **Verification**:
    - category=physical: value > 0, has_data=true
    - category=virtual: value > 0, has_data=true
    - Physical report value equals physical host contribution only (no virtual sockets)
    - Virtual report value equals virtual host contribution only (no physical sockets)
- **Expected Result**:
    - has_data and value are category-specific when multiple traditional categories contribute in the same org

**tally-report-has-data-nonpayg-TC004 - Cloud-only org daily has_data true on category=cloud only**

- **Description**: Verify a non-PAYG AWS public cloud host sets has_data=true on category=cloud daily report only; physical, virtual, and hypervisor stay has_data=false
- **Setup**:
    - Organization is opted in
    - One AWS public cloud non-PAYG host for RHEL for x86:
        - No marketplace extra facts (no aws_billing_products)
        - Normalized to 1 socket on instances report after tally
    - No physical, virtual, or hypervisor hosts contributing in org
- **Action**:
    - Run nightly tally
    - Query daily Sockets for category=cloud for a window including today
    - Query daily Sockets for category=physical, virtual, and hypervisor for the same day/window
- **Verification**:
    - category=cloud: latest (or today) data point has value > 0 and has_data=true
    - category=physical, virtual, hypervisor: value=0 and has_data=false for the same day
    - No data point in the cloud series has value=0 and has_data=true unless that day had no cloud contribution
- **Expected Result**:
    - Cloud has_data reflects cloud category presence only; other categories do not report has_data=true when empty

## Tally Summary Messages (Non-PAYG / Nightly)

### Message Verification

**tally-summary-nonpayg-TC001 - Nightly tally emits daily granularity**

- **Description**: Verify that nightly tally for traditional non-PAYG products produces summary messages with DAILY granularity
- **Setup**:
    - Organization is opted in
    - One physical RHEL host with 2 sockets seeded (shared non-PAYG fixture)
- **Action**:
    - Trigger nightly tally (snapshots creation)
    - Wait for tally summary Kafka messages on tally topic
- **Verification**:
    - Tally summary messages are received
    - Messages have DAILY granularity
    - Messages contain expected product (e.g. RHEL for x86) and metric (Sockets)
- **Expected Result**:
    - Nightly tally produces DAILY granularity summaries for non-PAYG products
    - Messages are published to tally topic

**tally-summary-nonpayg-TC002 - Nightly tally has no TOTAL measurements**

- **Description**: Verify that nightly tally DAILY snapshots for non-PAYG products do not emit TOTAL hardware measurement type
- **Setup**:
    - Organization is opted in
    - One physical RHEL host with 2 sockets seeded (shared non-PAYG fixture)
- **Action**:
    - Trigger nightly tally
    - Read DAILY tally summary from Kafka for sockets measurement
- **Verification**:
    - Summary messages are not empty
    - No measurements have hardware type TOTAL
    - All measurements are specific types (PHYSICAL, VIRTUAL, etc.)
- **Expected Result**:
    - Nightly snapshots do not include TOTAL aggregations
    - Only granular measurement types are present

**tally-summary-nonpayg-TC003 - Nightly gauge currentTotal equals value**

- **Description**: Verify gauge metric sockets in DAILY summary has currentTotal equals value (not a cumulative SUM)
- **Setup**:
    - Organization is opted in
    - One physical RHEL host with 2 sockets seeded
- **Action**:
    - Trigger nightly tally
    - Read DAILY tally summary from Kafka for Sockets measurement
- **Verification**:
    - Value is present and non-null
    - currentTotal is present and non-null
    - currentTotal equals value
- **Expected Result**:
    - Sockets measurement should be a point-in-time total, not month-to-date accumulation in currentTotal

**tally-summary-nonpayg-TC004 - Nightly gauge reflects current state not accumulation**

- **Description**: Verify gauge sockets reflects current inventory state when a second host is added, not historical accumulation
- **Setup**:
    - Organization is opted in
    - Capture baseline DAILY summary sockets value (may be 0)
    - Host A: 2 sockets, synced/tallied
    - Host B: 4 sockets, added after first nightly tally
- **Action**:
    - Run nightly tally after Host A; record value / currentTotal
    - Add Host B; run nightly tally again; record value / currentTotal
- **Verification**:
    - After first tally: currentTotal equals value; increase reflects Host A (+2 from prior baseline)
    - After second tally: currentTotal equals value; increase from first tally is +4 (Host B only), not +6 accumulated history
- **Expected Result**:
    - Gauge shows current socket total across hosts, not sum of all prior daily snapshot values

**tally-summary-nonpayg-TC005 - Nightly gauge drops when one host loses product buckets**

- **Description**: Verify nightly daily totals reflect current bucket state when one host no longer contributes RHEL for x86 buckets (decrement mirror of TC004)
- **Setup**:
    - Organization is opted in
    - Shared non-PAYG physical RHEL fixture (3 hosts, 12 sockets total)
- **Action**:
    - Run nightly tally
    - Query daily Sockets report (category=physical) and DAILY tally summary for RHEL for x86; record totals
    - Remove all host_tally_buckets for RHEL for x86 on one host (e.g. Host B, 6 sockets) — CT may use DB helper; production path is inventory reconcile marking buckets stale
    - Run nightly tally again
    - Query daily report and DAILY summary again
- **Verification**:
    - Before removal: daily report = 12; summary value and currentTotal both = 12
    - After removal: daily report = 6 (Hosts A + C only)
    - Summary value and currentTotal both = 6; decrease is −6 (removed host only), not a stale 12
    - Instances report meta.count decreases by 1 for the affected host
- **Expected Result**:
    - Nightly gauge totals drop when a host's product buckets are removed; snapshots do not retain the removed host's socket contribution

### Attribute partitioning

**tally-summary-by-attributes-nonpayg-TC001 - Nightly tally summary separates measurements by SLA**

- **Description**: Verify DAILY tally summary messages for traditional non-PAYG product separate measurement values by SLA
- **Setup**:
    - Organization is opted in
    - Host A: Premium SLA, 2 sockets
    - Host B: Standard SLA, 2 sockets
- **Action**:
    - Run nightly tally
    - Poll DAILY summaries for RHEL for x86
- **Verification**:
    - Snapshots exist for both Premium and Standard SLA values
    - Per-SLA sums are each > 0
    - Sum of Premium + Standard measurement values equals unfiltered total for the metric
- **Expected Result**:
    - Nightly tally produces separate DAILY snapshot measurements per SLA attribute value

**tally-summary-by-attributes-nonpayg-TC002 - Nightly tally summary separates measurements by usage**

- **Description**: Verify DAILY tally summary messages for traditional non-PAYG product separate measurement values by usage
- **Setup**:
    - Organization is opted in
    - Host A: Production usage
    - Host B: Development/Test usage
- **Action**:
    - Run nightly tally
    - Poll DAILY summaries for RHEL for x86
- **Verification**:
    - Snapshots contain both Production and Development/Test
    - Per-usage sums > 0
    - Per-usage sums add up to unfiltered total
- **Expected Result**:
    - Nightly tally produces separate DAILY snapshot measurements per usage attribute value

## Tally Summary Messages (PAYG / Hourly)

### Message Verification

**tally-summary-payg-TC001 - Hourly tally emits no daily granularity**

- **Description**: Verify that hourly tally for PAYG products does not produce summary messages with DAILY granularity
- **Setup**:
    - Organization is opted in
    - PAYG metering events for the last 4 hours
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with DAILY granularity
- **Verification**:
    - Expected message count is 0 for DAILY granularity
    - No DAILY messages are found
- **Expected Result**:
    - Hourly tally does not produce DAILY granularity summaries for PAYG products
    - Only HOURLY summaries are emitted

**tally-summary-payg-TC002 - Hourly tally emits hourly granularity**

- **Description**: Verify that hourly tally for PAYG products produces summary messages with HOURLY granularity and no TOTAL measurements
- **Setup**:
    - Organization is opted in
    - PAYG metering events for the last 4 hours
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Exactly 4 summary messages are received (one per hour)
    - All messages have HOURLY granularity
    - No measurements have hardware type "TOTAL"
- **Expected Result**:
    - Hourly tally produces HOURLY granularity summaries
    - One summary per event hour
    - TOTAL measurements are excluded

### Attribute partitioning

**tally-summary-by-attributes-payg-TC001 - Hourly tally summary separates measurements by SLA**

- **Description**: Verify HOURLY tally summary messages for PAYG product contain separate measurement values for each SLA level
- **Setup**:
    - Organization is opted in
    - PAYG events created for each SLA type (PREMIUM, STANDARD, SELF_SUPPORT)
    - One event with no SLA
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Sum of all per-SLA measurement values equals total minus the no-SLA value
    - Sum of per-SLA values plus no-SLA value equals the total
- **Expected Result**:
    - Hourly tally produces separate snapshot measurements for each SLA attribute value
    - No-SLA measurements are tracked separately from defined SLA values

**tally-summary-by-attributes-payg-TC002 - Hourly tally summary separates measurements by usage**

- **Description**: Verify HOURLY tally summary messages for PAYG product contain separate measurement values for each usage type
- **Setup**:
    - Organization is opted in
    - PAYG events created for each Usage type (PRODUCTION, DEVELOPMENT_TEST, DISASTER_RECOVERY)
    - One event with no Usage
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Sum of all per-usage measurement values equals total minus the no-usage value
    - Sum of per-usage values plus no-usage value equals the total
- **Expected Result**:
    - Hourly tally produces separate snapshot measurements for each usage attribute value
    - No-usage measurements are tracked separately from defined usage values

**tally-summary-by-attributes-payg-TC003 - Hourly tally summary separates measurements by billing account ID**

- **Description**: Verify HOURLY tally summary messages for PAYG product contain separate measurement values for each billing account ID
- **Setup**:
    - Organization is opted in
    - Two PAYG events from different instances with different billing account IDs and different metric values
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Each billing account ID has its expected measurement value
    - Sum of per-billing-account values equals the total
- **Expected Result**:
    - Hourly tally produces separate snapshot measurements for each billing account ID
    - Per-billing-account measurement values sum to the overall total

**tally-summary-by-attributes-payg-TC004 - Hourly tally summary attributes measurements when a host changes billing account ID**

- **Description**: Verify that when a single PAYG host sends events under different billing account IDs at different times, hourly tally correctly attributes each measurement to the respective billing account
- **Setup**:
    - Organization is opted in
    - Same host sends one event with billing account A at T-2 hours
    - Same host sends another event with billing account B at T-1 hours
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Billing account A has the measurement value from the first event (5.0)
    - Billing account B has the measurement value from the second event (8.0)
    - Sum of per-billing-account values equals the total
- **Expected Result**:
    - Hourly tally correctly attributes measurements to the billing account from each event
    - A billing account change on a single instance does not merge or lose measurement values

**tally-summary-by-attributes-payg-TC005 - Hourly tally summary separates measurements by billing provider**

- **Description**: Verify HOURLY tally summary messages for PAYG product contain separate measurement values for each billing provider
- **Setup**:
    - Organization is opted in
    - Two PAYG events from different instances with different billing providers (AWS, Azure) and different metric values
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Each billing provider has its expected measurement value
    - Sum of per-billing-provider values equals the total
- **Expected Result**:
    - Hourly tally produces separate snapshot measurements for each billing provider attribute value
    - Per-billing-provider measurement values sum to the overall total

## Instance Reporting for Billing Account IDs

**tally-instances-billing-account-TC001 - Billing account IDs exclude old month data**

- **Description**: Verify that the billing account IDs endpoint only returns accounts with activity in the current month
- **Setup**:
    - Organization is opted in
    - Host with billing account ID created with last_seen at the first instant of the previous calendar month (UTC), e.g. YearMonth.now(UTC).minusMonths(1).atDay(1).atStartOfDay()
- **Action**:
    - Call get billing account IDs endpoint
- **Verification**:
    - Response does not contain the billing account from last month
    - Only current month billing accounts are included
- **Expected Result**:
    - Service filters billing accounts by current month boundary
    - Old billing account data is excluded from response

**tally-instances-billing-account-TC002 - Multiple billing account IDs returned**

- **Description**: Verify that the billing account IDs endpoint returns distinct billing accounts after PAYG events are ingested and tallied
- **Setup**:
    - Organization is opted in
    - Two PAYG instance events produced to ingress with different billing account IDs
    - Same product tag and AWS billing provider on both events
- **Action**:
    - Perform hourly tally until materialized
    - Call get billing account IDs endpoint
- **Verification**:
    - Response contains exactly 2 billing account entries
    - Both billing account IDs are present in response
    - Each entry has correct org_id, product_tag, and billing_provider fields
    - Billing provider is set to "aws"
- **Expected Result**:
    - Each distinct billing account from tallied activity appears once in the list
    - Response structure includes all required fields
    - Multiple billing accounts are properly differentiated

**tally-instances-billing-account-TC003 - Billing account IDs: three distinct accounts**

- **Description**: Verify the billing_account_ids endpoint with three tuples
- **Setup**:
    - Three PAYG events with distinct billing account IDs, same provider/product
- **Action**:
    - Hourly tally; call billing account IDs
- **Verification**:
    - Three entries; each id present with correct org, product_tag, provider
- **Expected Result**:
    - Endpoint scales to multiple accounts beyond the pair in TC002

**tally-instances-billing-account-TC004 - Billing account IDs: duplicate billing account across instances**

- **Description**: Verify two PAYG instance events that share the same billing account ID dedupe to a single billing_account_ids row
- **Setup**:
    - Organization is opted in
    - Two PAYG events with the same billing account ID and AWS billing provider, different instance IDs; hourly tally materialized
- **Action**:
    - Call billing account IDs endpoint
- **Verification**:
    - Exactly one row for the shared billing account ID (deduplicated, not two)
    - That row has the expected org_id, product_tag, and billing_provide (aws)
- **Expected Result**:
    - Duplicate billing account activity collapses to one stable list entry per org/product/provider tuple

**tally-instances-billing-account-TC005 - Billing account IDs: mixed billing providers**

- **Description**: Verify entries include distinct billing_provider values
- **Setup**:
    - Events with different billing_provider values
- **Action**:
    - Call billing account IDs
- **Verification**:
    - Each returned tuple has the expected provider field per event
- **Expected Result**:
    - Provider dimension is visible in the billing account list

## Instance Reporting with Parameters for PAYG Products

**tally-instances-payg-TC001 - PAYG instances metered by month boundary**

- **Description**: Verify that PAYG instances are metered and reported based on the month boundary of the event timestamp
- **Setup**:
    - Organization is opted in
    - Event created with timestamp from first day of previous month
    - Event includes billing account ID and AWS provider information
- **Action**:
    - Produce event to Kafka
    - Perform hourly tally
    - Query instances for current month
    - Query instances for previous month
- **Verification**:
    - Current month instances report shows 0 metered value
    - Previous month instances report shows metered value > 0
    - Metered values are attributed to the month of the event
- **Expected Result**:
    - Service assigns metered values to the appropriate month
    - Month boundaries are respected for PAYG billing
    - Instance data is segregated by month

**tally-instances-payg-TC002 - Instances report filtered by SLA**

- **Description**: Verify that SLA query parameter restricts rows to hosts matching that service level when two instances differ only by SLA
- **Setup**:
    - Organization is opted in
    - Two PAYG events in the same time window with distinct SLA values
- **Action**:
    - Hourly tally; query instances by product with SLA value set to first host’s SLA
    - Repeat with SLA value set to second host’s SLA
- **Verification**:
    - Each query returns only the matching instance (or equivalent count)
    - Query with a non-existent SLA combination returns no matching rows
- **Expected Result**:
    - SLA filter is applied consistently on the instances API

**tally-instances-payg-TC003 - Instances report filtered by usage**

- **Description**: Verify that usage query parameter restricts rows when two instances differ only by usage type
- **Setup**:
    - Organization is opted in
    - Two PAYG events in the same window with distinct usage values
- **Action**:
    - Query instances with usage matching each event in turn
- **Verification**:
    - Only the matching usage row appears per query
- **Expected Result**:
    - Usage filter behaves as documented for instances

**tally-instances-payg-TC004 - Instances report filtered by billing provider**

- **Description**: Verify that billing_provider restricts rows when instances differ only by provider
- **Setup**:
    - Organization is opted in
    - Two events with different billing providers, same product and window
- **Action**:
    - Query instances with each billing_provider value
- **Verification**:
    - Each query returns only instances for that provider
- **Expected Result**:
    - Billing provider filter is enforced on the instances report

**tally-instances-payg-TC005 - Instances report excludes wrong billing account**

- **Description**: Verify that querying with a non-matching billing_account_id returns no instance rows
- **Setup**:
    - Organization is opted in
    - One PAYG event with a known billing account ID
- **Action**:
    - Hourly tally; query instances with billing_account_id set to a different UUID than the event
- **Verification**:
    - Response has no data rows (or zero measurements) for the mismatched account
- **Expected Result**:
    - Billing account filter rejects non-matching accounts

**tally-instances-payg-TC006 - Instances report with all optional filters and meta**

- **Description**: Verify response meta echoes sla, usage, billing_provider, and billing_account_id when all are supplied
- **Setup**:
    - One event whose attributes align with all filter values
- **Action**:
    - Query with every optional filter set to that event’s values
- **Verification**:
    - Meta fields reflect the query parameters; data includes the instance
- **Expected Result**:
    - Full filter surface is consistent for a single matching host

**tally-instances-payg-TC007- Partial filters return multiple billing accounts**

- **Description**: Verify that omitting billing_account_id returns both instances when they share SLA and usage but differ by billing account
- **Setup**:
    - Two instances: same SLA and usage, different billing_account_id
- **Action**:
    - Query with sla and usage only (no billing account filter)
- **Verification**:
    - Two rows (or meta.count reflects both)
- **Expected Result**:
    - Partial filters do not over-restrict when account is omitted

**tally-instances-payg-TC008 - Partial filters narrow to one billing account**

- **Description**: Verify combining sla, usage, and billing_account_id returns a single row
- **Setup**:
    - Two instances: same SLA and usage, different billing_account_id
- **Action**:
    - Query with all three dimensions set to one host’s values
- **Verification**:
    - Exactly one instance in the result set
- **Expected Result**:
    - Account filter distinguishes otherwise identical rows

**tally-instances-payg-TC009 - No optional filters returns full in-range set**

- **Description**: Verify that only beginning, ending, and product identify all tallied instances in range (no SLA/usage/provider/account params)
- **Setup**:
    - Multiple instances in the same valid time range
- **Action**:
    - Query without optional filters
- **Verification**:
    - All seeded instances appear; meta.count matches
- **Expected Result**:
    - Default query path returns complete in-window population

**tally-instances-payg-TC010 - Two events in different months; current month query**

- **Description**: Verify month discrimination when two events exist: one instance active last month, one this month; querying current month returns only the current-month instance
- **Setup**:
    - Two PAYG events with different instance IDs and timestamps in adjacent months
- **Action**:
    - Query instances with beginning/ending covering current month only
- **Verification**:
    - Only the current-month instance id is present
    - Last month’s is absent
- **Expected Result**:
    - Multi-event orgs do not leak prior-month instances into the current window

**tally-instances-payg-TC011 - PAYG instances API rejects cross-month beginning/ending**

- **Description**: Verify that beginning and ending for a PAYG product must fall in the same calendar month; otherwise the API returns HTTP 400 Bad Request
- **Setup**:
    - Organization is opted in (shared PAYG instances fixture)
- **Action**:
    - Call instances by product with beginning in one month and ending in the next month (e.g. 10th 12:00 UTC to following month 10th 12:00 UTC)
- **Verification**:
    - Response status is 400
    - Error payload references the same-month restriction
- **Expected Result**:
    - Invalid date ranges are rejected before instance data is returned

**tally-instances-payg-TC012 - PAYG instances report over full UTC calendar month**

- **Description**: Verify instances can be queried with beginning at the first instant of the month and ending at the last instant of that month (full calendar span), not only [firstOfMonth, now] partial windows used elsewhere
- **Setup**:
    - Shared PAYG instances fixture from TallyInstancesReportFiltersPaygTest (setupSharedFixture); filter by sla.billingAccountId() — a billing account seeded with current-month Premium SLA metered rows
- **Action**:
    - Query instances for the product with month start 00:00:00 UTC through month end 23:59:59.999 UTC and billing_account_id set
- **Verification**:
    - At least one data row
    - Summed metered measurements > 0
- **Expected Result**:
    - Full-month same-month windows return expected PAYG instance rows for the fixture

**tally-instances-payg-TC013 - PAYG instances report has two rows for distinct Ansible events**

- **Description**: Verify hourly tally materializes one instances-report row per distinct instance_id, applying only that event's measurements (swatch does not remap metrics by producer role)
- **Setup**:
    - Organization opted in
    - Publish two Kafka events for ansible-aap-managed (same billing account / window):
        - Event A: distinct instance_id A, measurement Managed-nodes = 2.0 only
        - Event B: distinct instance_id B, measurement Instance-hours = 1.0 only
- **Action**:
    - Run hourly tally
    - Query instances report for ansible-aap-managed in event window
    - Locate rows by instance_id A and B
- **Verification**:
    - Exactly two instance rows; meta.count = 2
    - Row A: Managed-nodes = 2.0; no Instance-hours measurement on that row
    - Row B: Instance-hours = 1.0; no Managed-nodes measurement on that row
- **Expected Result**:
    - Each event's instance_id becomes its own instances row with only the metrics and values present on that event

## Instance Reporting based on Pagination and Sorting

**tally-instances-sorting-TC001 - Pagination limit and offset**

- **Description**: Verify limit and offset slice results while meta.count reflects total matches
- **Setup**:
    - At least three instances matching the same filter bucket
- **Action**:
    - Query with limit=2, offset=0; then offset=2
- **Verification**:
    - First page has two rows; second page has remainder; total count unchanged
- **Expected Result**:
    - Pagination parameters behave per API contract

**tally-instances-sorting-TC002 - Pagination links when offset or limit present**

- **Description**: Verify links object when pagination query params are used (complements TC013 row counts)
- **Setup**:
    - At least three instances matching the same filter bucket
- **Action**:
    - Issue requests with and without pagination params
- **Verification**:
    - Links present when offset or limit set; meta.count stable across pages
- **Expected Result**:
    - Pagination navigation is populated as implemented

**tally-instances-sorting-TC003 - Sort by last_seen ascending and descending**

- **Description**: Verify sort and dir change ordering (e.g. last_seen or display_name)
- **Setup**:
    - Two instances with distinguishable last_seen or names
- **Action**:
    - Query with sort + dir=asc and dir=desc
- **Verification**:
    - Order reverses between calls
- **Expected Result**:
    - Sort parameters affect instance list ordering

**tally-instances-sorting-TC004- Sort by display_name**

- **Description**: Verify substring match on host display name
- **Setup**:
    - Two instances with different display names
- **Action**:
    - Query with display_nam matching one name only
- **Verification**:
    - Single matching row; non-matching substring returns empty
- **Expected Result**:
    - Display name sorting is applied and shows expected results

**tally-instances-sorting-TC005 - Sort by metric_id**

- **Description**: Verify metric_id restricts rows when instances differ by billable metric (e.g. cores vs sockets), aligned with server-side measurement logic
- **Setup**:
    - Two instances contributing under different metric IDs where the product supports both
- **Action**:
    - Query with each metric_id
- **Verification**:
    - Only the relevant instance appears per metric filter
- **Expected Result**:
    - Metric sorting works and shows expected results

**tally-instances-sorting-TC006 - Sort by report category**

- **Description**: Verify category (report category / hardware measurement grouping) when instances differ by category
- **Setup**:
    - Events or hosts that map to distinct categories for the product
- **Action**:
    - Query with each category value
- **Verification**:
    - Only matching category rows return
- **Expected Result**:
    - Category sorting works and shows expected results

**tally-instances-sorting-TC007 - Pagination limit and offset (non-PAYG)**

- **Description**: Verify limit/offset paging and meta.count for non-PAYG physical instances
- **Setup**:
    - Organization is opted in
    - Three hosts: meta.count must be 3
- **Action**:
    - Run nightly tally
    - Query unfiltered merged set (reference count = 3)
    - Query with limit=1, offset=0
    - Query with limit=1, offset=1
- **Verification**:
    - Each paged response has at most 1 row
    - Both responses report meta.count = 3
- **Expected Result**:
    - Pagination limits rows but preserves total count metadata

**tally-instances-sorting-TC008 - Pagination links when offset or limit present (non-PAYG)**

- **Description**: Verify pagination links and page sizing for non-PAYG instances
- **Setup**:
    - Organization is opted in
    - Three hosts
- **Action**:
    - Run nightly tally
    - Query without limit (expect 3 rows)
    - Query with limit=2, offset=0
- **Verification**:
    - Unpaged: 3 rows
    - Limited: at most 2 rows; meta present
    - If links object present, at least one of first/last/previous/next is non-null
- **Expected Result**:
    - Pagination metadata and links behave for non-PAYG product (no PAYG same-month restriction)

**tally-instances-sorting-TC009 - Sort by number_of_guests ascending and descending (hypervisor)**

- **Description**: Verify instances API sorts hypervisor category by number_of_guests asc/desc
- **Setup**:
    - At least two hypervisor hosts with different guest counts in org
- **Action**:
    - Run nightly tally
    - Query hypervisor instances unfiltered; collect guest counts
    - Query with sort=number_of_guests, dir=asc
    - Query with sort=number_of_guests, dir=desc
- **Verification**:
    - Ascending guest list equals sorted ascending of unfiltered list
    - Descending guest list equals sorted descending of unfiltered list
- **Expected Result**:
    - Hypervisor sort by guest count works on instances report

**tally-instances-sorting-TC010 - Sort by sockets ascending and descending (non-PAYG)**

- **Description**: Verify instances API sorts non-PAYG nightly hosts by sockets asc/desc (non-PAYG-only sort field)
- **Setup**:
    - Organization is opted in
    - At least two physical non-PAYG hosts with different socket counts in buckets (e.g. 2 and 6)
- **Action**:
    - Run nightly tally
    - Query instances for the product; note socket values per row
    - Query with sort=sockets, dir=asc
    - Query with sort=sockets, dir=desc
- **Verification**:
    - Ascending order matches increasing socket counts
    - Descending order reverses ascending order
- **Expected Result**:
    - Sockets sort parameter is honored for non-PAYG instances report

**tally-instances-sorting-TC011 - Sort by cores ascending and descending (non-PAYG)**

- **Description**: Verify instances API sorts non-PAYG nightly hosts by cores asc/desc (non-PAYG-only sort field)
- **Setup**:
    - Organization is opted in
    - At least two physical non-PAYG hosts with different core counts in buckets (e.g. 2 and 8)
- **Action**:
    - Run nightly tally
    - Query instances for the product; note core values per row
    - Query with sort=cores, dir=asc
    - Query with sort=cores, dir=desc
- **Verification**:
    - Ascending order matches increasing core counts
    - Descending order reverses ascending order
- **Expected Result**:
    - Cores sort parameter is honored for non-PAYG instances report

## Instance Reporting with Parameters for Non-PAYG Products

**tally-instances-nonpayg-TC001 - Physical RHEL unfiltered instances report lists all hosts**

- **Description**: Verify unfiltered instances report lists all three physical RHEL hosts with correct socket totals
- **Setup**:
    - Organization opted in
    - Three hosts with sockets: A (4/Premium/Production), B (6/Standard/Development/Test), C (2/Premium/Development/Test)
- **Action**:
    - Run nightly tally
    - Query instances API: product RHEL for x86, category physical, metric sockets, today's window
- **Verification**:
    - meta.count equals 3
    - Row count equals 3
    - Display names match fixture hosts
    - Sum of row socket measurements equals 12
- **Expected Result**:
    - Unfiltered physical instances report reflects full org state

**tally-instances-nonpayg-TC002 - Physical RHEL instances SLA filter partitions meta.count and sockets**

- **Description**: Verify SLA filters return correct socket totals and row counts
- **Setup**:
    - Organization opted in
    - Three hosts: Premium=2 hosts, Standard=1 host
- **Action**:
    - Run nightly tally
    - Query with each SLA value (Premium, Standard, Self-Support, ``)
- **Verification**:
    - Premium: 6 sockets, 2 rows
    - Standard: 6 sockets, 1 row
    - Self-Support and empty SLA: 0 rows
    - Sum of SLA bucket meta.count values equals unfiltered meta.count (3)
- **Expected Result**:
    - SLA filtering and meta.count partition behave correctly for non-PAYG physical hosts

**tally-instances-nonpayg-TC003 - Physical RHEL instances usage filter partitions meta.count and sockets**

- **Description**: Verify usage filters return correct totals
- **Setup**:
    - Organization opted in
    - Three hosts: Production=1 host, Development/Test=2 hosts
- **Action**:
    - Run nightly tally
    - Query all usage types (Production, Development/Test, Disaster Recovery, ``)
- **Verification**:
    - Production: 4 sockets, 1 row
    - Development/Test: 8 sockets, 2 rows
    - Disaster Recovery and empty usage: 0 rows
    - Usage bucket counts sum to 3
- **Expected Result**:
    - Usage filtering and meta.count partition are consistent

**tally-instances-nonpayg-TC004 - Physical RHEL instances combined SLA and usage filters narrow to one host**

- **Description**: Verify combined SLA+usage filters return expected single-host rows
- **Setup**:
    - Organization opted in
    - Three hosts: each valid SLA+usage pair maps to one host; Standard+Production has no matching host
- **Action**:
    - Run nightly tally
    - Query Premium + Production
    - Query Standard + Development/Test
    - Query Premium + Development/Test
    - Query Standard + Production
- **Verification**:
    - Premium+Production: 4 sockets, 1 row
    - Standard+Dev/Test: 6 sockets, 1 row
    - Premium+Dev/Test: 2 sockets, 1 row
    - Standard+Production: 0 rows, 0 sockets
- **Expected Result**:
    - Combined filters narrow to exactly one host per valid SLA/usage pair

**tally-instances-nonpayg-TC005 - Physical non-PAYG instances exclude AWS billing provider filter**

- **Description**: Verify non-PAYG physical instances do not match marketplace billing provider filters
- **Setup**:
    - Organization opted in
    - Physical host exists with no billing_provider set
- **Action**:
    - Run nightly tally
    - Query instances with billing_provider=aws, category physical
- **Verification**:
    - Zero rows returned
- **Expected Result**:
    - AWS billing provider filter excludes traditional non-PAYG hosts

**tally-instances-nonpayg-TC006 - Physical non-PAYG instances exclude non-matching billing account ID**

- **Description**: Verify random billing account ID does not match non-PAYG physical rows
- **Setup**:
    - Organization opted in
    - Physical host exists with no billing_account_id set
- **Action**:
    - Run nightly tally
    - Query with random UUID billing_account_id
- **Verification**:
    - Zero rows returned
- **Expected Result**:
    - Billing account filter correctly returns empty for non-matching account

**tally-instances-nonpayg-TC007 - Physical host SLA and usage change migrates instances filter buckets**

- **Description**: Verify after host SLA/usage changes, instances appear under new filters and disappear from old filters
- **Setup**:
    - Organization opted in
    - One physical host: Premium + Production (4 sockets)
- **Action**:
    - Confirm host under Premium + Production filters after nightly tally
    - Update host to Standard + Development/Test
    - Run nightly tally again
- **Verification**:
    - Host present under Standard + Development/Test
    - Host absent under Premium + Production
    - last_seen increases after update
- **Expected Result**:
    - Tally migrates host across SLA/usage partition buckets on host attribute change

**tally-instances-nonpayg-TC008 - Marketplace AWS cloud host instances report shows zero sockets**

- **Description**: Verify a non-PAYG AWS marketplace public cloud host appears on the instances report as category=cloud with zero sockets, even when raw socket count is high
- **Setup**:
    - Organization opted in
    - Seed one AWS public cloud host for RHEL for x86:
        - number_of_sockets=7, cores_per_socket=3 (high raw count)
        - Marketplace extra fact: aws_billing_products=yes
        - is_virtual=true, product tag rhel-for-x86
- **Action**:
    - Run nightly tally
    - Query instances report for RHEL for x86, metric Sockets, today's window, host display name
- **Verification**:
    - Exactly one row for the host
    - category=cloud
    - cloud_provider=aws
    - Sockets measurement = 0
- **Expected Result**:
    - Marketplace public cloud hosts normalize to zero sockets on the instances report; raw socket facts do not leak through

**tally-instances-nonpayg-TC009 - Non-marketplace AWS cloud host instances report normalizes to one socket**

- **Description**: Verify a non-PAYG AWS public cloud host without marketplace facts appears on the instances report as category=cloud with one normalized socket
- **Setup**:
    - Organization opted in
    - Seed one AWS public cloud host for RHEL for x86:
        - number_of_sockets=2, cores_per_socket=1
        - No marketplace extra facts (no aws_billing_products)
        - is_virtual=true, product tag rhel-for-x86
- **Action**:
    - Run nightly tally
    - Query instances report for RHEL for x86, metric sockets, today's window, host display name
- **Verification**:
    - Exactly one row for the host
    - category=cloud
    - cloud_provider=aws
    - Sockets measurement = 1
- **Expected Result**:
    - Non-marketplace public cloud RHEL contributes one socket on the instances report regardless of raw socket count > 1

**tally-instances-nonpayg-TC010 - Virtual RHEL instances meta.count increases for matching SLA and usage**

- **Description**: Verify adding a virtual RHEL host increases instances report meta.count by 1 under category=virtual for matching SLA and usage
- **Setup**:
    - Organization is opted in
    - Baseline instances query: category=virtual, chosen SLA/usage, metric Sockets, today's window
    - One additional virtual host seeded with matching SLA and usage
- **Action**:
    - Run nightly tally
    - Query instances report with category=virtual, same SLA and usage
- **Verification**:
    - meta.count increases by 1 from baseline
    - New row display_name matches seeded virtual host
    - category=virtual
- **Expected Result**:
    - Virtual instances API filters and meta.count track new virtual hosts

**tally-instances-nonpayg-TC011 - Virtual RHEL instance sockets normalized to one**

- **Description**: Verify nightly tally normalizes an unmapped virtual RHEL guest from a high raw socket fact (5) to Sockets = 1.0 on the instances report
- **Setup**:
    - Organization is opted in
    - Inventory-driven seed: virtual RHEL unmapped guest in HBI/inventory with system_profile.number_of_sockets = 5, product RHEL for x86 / rhel-for-x86, no hypervisor mapping
- **Action**:
    - Run nightly tally (reconcile from inventory)
    - Query instances report for host by display_name, category=virtual, metric Sockets
- **Verification**:
    - Exactly one row for the host
    - category=virtual
    - Sockets measurement = 1.0 (normalized from raw 5, not 5.0)
- **Expected Result**:
    - Virtual guests contribute one socket on instances report after tally reconcile normalizes raw inventory facts

## Version API

**version-TC001 - Build metadata from version endpoint**

- **Description**: Verify `GET /v1/version` returns service metadata for the deployed swatch-tally application
- **Setup**: Component test environment with swatch-tally running
- **Action**: Call the public version endpoint
- **Verification**:
    - HTTP 200
    - Response includes non-empty `build.version`, `build.artifact`, `build.name`, and
      `build.group`
    - `build.version` is the git commit SHA (7–40 hex chars)
    - `build.artifact` is `swatch-tally`
- **Expected Result**: Version endpoint is available and exposes build info suitable for test-run traceability

## Seeding the HBI database for Swatch Tally

**hbi-data-seeder-TC001 - Insert a host in the HBI database **

- **Description**: Verify that we can insert a host in the HBI database
- **Setup**: Component test environment with swatch-tally is running and an instance of insights db is up 
- **Action**: Insert a host into the HBI database 
- **Verification**:
  - a host was returned from the insert
  - the host returned has a inventory id 
  - the host returned has a subscription manager id 
  - the host returned has the expected orgId
- **Expected Result**: The host was inserted into the HBI database


**hbi-data-seeder-TC002 - Insert a host in the HBI database and delete it **

- **Description**: Verify that we can delete an inserted host in the HBI database
- **Setup**: Component test environment with swatch-tally is running, an instance of insights db is up and have a host 
  in the HBI database
- **Action**: delete the host previously inserted in the HBI database
- **Verification**:
  - the host was deleted from the database
- **Expected Result**: The host was deleted from the database


**hbi-data-seeder-TC003 - The rollback deletes all the inserted hosts from the HBI database **

- **Description**: Verify that we can delete all inserted hosts in the HBI database
- **Setup**: Component test environment with swatch-tally is running, an instance of insights db is up and have more 
    than one host in the HBI database
- **Action**: run the rollback
- **Verification**:
  - ensure that all the inserted hosts that were inserted are deleted from the database
- **Expected Result**: The host was deleted from the database


**hbi-data-seeder-TC004 - Insert a RHEL host in the HBI database **

- **Description**: Verify that we can insert a RHEL product into the the HBI database
- **Setup**: Component test environment with swatch-tally is running, an instance of insights db 
- **Action**: Create a host with a product that is a RHEL product
- **Verification**:
  - verify that a tally Report for the RHEL product is not null 
  - verify that the total sockets value in the tally Report is greather that or equal to the 2 sockets
- **Expected Result**: All the host inserted into the db have been deleted from the database


**hbi-data-seeder-TC005- Insert a NON RHEL host in the HBI database **

- **Description**: Verify that we can insert a RHEL product into the the HBI database
- **Setup**: Component test environment with swatch-tally is running, an instance of insights db
- **Action**: Create a host with a product that is a NON RHEL product
- **Verification**:
  - verify that the host is non-null
  - verify that the host is exist in the HBI database
  - verify that you can sync tally without any errors
- **Expected Result**: All the host inserted into the db have been deleted from the database


## Nightly Tally with the HBI database

**nightly-tally-TC001 - Validate tally on physical RHEL sockets with socket increase mapping **

- **Description**: Verify that the tally data shows increase count and system table show the correct data
- **Setup**: Component test environment with swatch-tally is running and an instance of insights db is up, your org is 
     opted in and you have the current sockets count
- **Action**: Insert a rhel host into the HBI database and run the tally 
- **Verification**:
  - verify that initial sockets count plus the expected reported sockets equals the current sockets count 
  - verify that the instance report, instance's 'measurements' is not null
  - verify that the following are as expected: 
    - Display name 
    - category
    - expected reported sockets count 
- **Expected Result**: The tally data shows increase count and system table show the correct data

