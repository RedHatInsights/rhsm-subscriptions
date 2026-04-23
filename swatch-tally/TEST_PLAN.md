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

## Report Granularity and Filtering

### Test Organization

The tally report filtering test cases are organized into two component test files:

- **TallyReportFiltersPaygTest.java** - Contains 20 test cases (TC001-TC023, excluding TC009, TC010, TC015, TC024) covering PAYG (Pay-As-You-Go) scenarios:
  - TC001-TC008: Basic filtering by granularity, SLA, usage, billing provider, and billing account ID
  - TC011-TC014: Validation errors and metadata verification
  - TC016-TC023: Daily granularity filtering, monthly/quarterly/yearly granularity support
  - Product: RHEL for x86 ELS PAYG (supports hourly granularity)

- **TallyReportFiltersEdgeCaseTest.java** - Contains 4 test cases for edge cases requiring special event patterns:
  - TC009: Multiple events aggregation with same filter attributes
  - TC010: Three distinct SLA values filtering
  - TC015: Data gaps with hasData field
  - TC024: Billing account change for same instance
  - Product: RHEL for x86 ELS PAYG (supports hourly granularity)

All test files use `@BeforeAll` to create test data once and share it across all test methods. Each test is parameterized with `@ValueSource(booleans = {true, false})` to verify behavior with both the legacy query path and the primary row searches feature flag enabled.

**tally-report-filters-TC001 - Daily granularity with all filters**

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

**tally-report-filters-TC002 - Hourly granularity filtered by SLA**

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

**tally-report-filters-TC003 - Hourly granularity filtered by usage**

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

**tally-report-filters-TC004 - Hourly granularity filtered by billing provider**

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

**tally-report-filters-TC005 - Hourly granularity filtered by billing account ID**

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

**tally-report-filters-TC006 - Daily granularity with partial filters**

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

**tally-report-filters-TC007 - Hourly granularity with all filters**

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

**tally-report-filters-TC008 - Invalid request without granularity**

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

**tally-report-filters-TC009 - Multiple events with same filter values are aggregated**

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

**tally-report-filters-TC010 - Three distinct filter value combinations**

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

**tally-report-filters-TC011 - Invalid request without beginning timestamp**

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

**tally-report-filters-TC012 - Invalid request without ending timestamp**

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

**tally-report-filters-TC013 - Metadata reflects no filters when omitted**

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

**tally-report-filters-TC014 - Metadata with EMPTY filter value**

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

**tally-report-filters-TC015 - Data gaps indicated by hasData field**

- **Description**: Verify that tally report data points correctly indicate gaps in time series data using the hasData field
- **Setup**:
    - Organization is opted in
    - Event created at hour 0 (value 10.0)
    - Event created at hour 2 (value 20.0)
    - Hour 1 has no events (gap)
    - Hourly tally is performed
- **Action**:
    - Request tally report for 4-hour range covering all hours
- **Verification**:
    - Response data contains data points
    - Data points have hasData field populated
    - At least one data point has hasData=true where events occurred
    - hasData field indicates presence/absence of actual event data
- **Expected Result**:
    - API populates hasData field in data points
    - hasData field accurately reflects whether events existed for that time period
    - Time series gaps are identifiable via hasData field

**tally-report-filters-TC016 - All data returned when no optional filters applied**

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

**tally-report-filters-TC017 - Daily granularity filtered by SLA after nightly tally**

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

**tally-report-filters-TC018 - Daily granularity filtered by usage after nightly tally**

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

**tally-report-filters-TC019 - Daily granularity filtered by billing provider after nightly tally**

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

**tally-report-filters-TC020 - Daily granularity filtered by billing account ID after nightly tally**

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

**tally-report-filters-TC021 - Monthly granularity with all filters**

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

**tally-report-filters-TC022 - Quarterly granularity with all filters**

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

**tally-report-filters-TC023 - Yearly granularity with all filters**

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

**tally-report-filters-TC024 - Daily report tracks billing account change for same instance**

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

## Summary Messages Separated By Attribute Value

**tally-summary-by-attributes-TC001 - Tally summary separates measurements by SLA**

- **Description**: Verify that tally summary messages contain separate measurement values for each SLA level
- **Setup**:
    - Events created for each SLA type (PREMIUM, STANDARD, SELF_SUPPORT)
    - One event with no SLA
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Sum of all per-SLA measurement values equals total minus the no-SLA value
    - Sum of per-SLA values plus no-SLA value equals the total
- **Expected Result**:
    - Tally produces separate snapshot measurements for each SLA attribute value
    - No-SLA measurements are tracked separately from defined SLA values

**tally-summary-by-attributes-TC002 - Tally summary separates measurements by usage**

- **Description**: Verify that tally summary messages contain separate measurement values for each usage type
- **Setup**:
    - Events created for each Usage type (PRODUCTION, DEVELOPMENT_TEST, DISASTER_RECOVERY)
    - One event with no Usage
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Sum of all per-usage measurement values equals total minus the no-usage value
    - Sum of per-usage values plus no-usage value equals the total
- **Expected Result**:
    - Tally produces separate snapshot measurements for each usage attribute value
    - No-usage measurements are tracked separately from defined usage values

**tally-summary-by-attributes-TC003 - Tally summary separates measurements by billing account ID**

- **Description**: Verify that tally summary messages contain separate measurement values for each billing account ID
- **Setup**:
    - Two events created from different instances with different billing account IDs and different metric values
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Each billing account ID has its expected measurement value
    - Sum of per-billing-account values equals the total
- **Expected Result**:
    - Tally produces separate snapshot measurements for each billing account ID
    - Per-billing-account measurement values sum to the overall total

**tally-summary-by-attributes-TC004 - Tally summary correctly attributes measurements when a single host changes billing account ID**

- **Description**: Verify that when a single host sends events under different billing account IDs at different times, tally correctly attributes each measurement to the respective billing account
- **Setup**:
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
    - Tally correctly attributes measurements to the billing account from each event
    - A billing account change on a single instance does not merge or lose measurement values

**tally-summary-by-attributes-TC005 - Tally summary separates measurements by billing provider**

- **Description**: Verify that tally summary messages contain separate measurement values for each billing provider
- **Setup**:
    - Two events created from different instances with different billing providers (AWS, Azure) and different metric values
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Each billing provider has its expected measurement value
    - Sum of per-billing-provider values equals the total
- **Expected Result**:
    - Tally produces separate snapshot measurements for each billing provider attribute value
    - Per-billing-provider measurement values sum to the overall total

## Tally Summary Messages

**tally-summary-TC001 - Nightly tally emits daily granularity**

- **Description**: Verify that nightly tally operation produces summary messages with DAILY granularity
- **Setup**:
    - Organization is opted in
    - Nightly tally host buckets are seeded in database
- **Action**:
    - Trigger nightly tally (snapshots creation)
    - Wait for tally summary Kafka messages
- **Verification**:
    - Tally summary messages are received
    - Messages have DAILY granularity
    - Messages contain expected product and metric
- **Expected Result**:
    - Nightly tally produces DAILY granularity summaries
    - Messages are published to tally topic

**tally-summary-TC002 - Nightly tally has no TOTAL measurements**

- **Description**: Verify that nightly tally snapshots do not emit TOTAL hardware measurement type
- **Setup**:
    - Organization is opted in
    - Nightly tally host buckets are seeded
- **Action**:
    - Trigger nightly tally
    - Wait for and capture tally summary messages
- **Verification**:
    - Summary messages are not empty
    - No measurements have hardware type "TOTAL"
    - All measurements are specific types (PHYSICAL, VIRTUAL, etc.)
- **Expected Result**:
    - Nightly snapshots do not include TOTAL aggregations
    - Only granular measurement types are present

**tally-summary-TC003 - Hourly tally emits no daily granularity**

- **Description**: Verify that hourly tally operation does not produce summary messages with DAILY granularity
- **Setup**:
    - Organization is opted in
    - Events for last 4 hours are created
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with DAILY granularity
- **Verification**:
    - Expected message count is 0 for DAILY granularity
    - No DAILY messages are found
- **Expected Result**:
    - Hourly tally does not produce DAILY granularity summaries
    - Only HOURLY summaries are emitted

**tally-summary-TC004 - Hourly tally emits hourly granularity**

- **Description**: Verify that hourly tally operation produces summary messages with HOURLY granularity and no TOTAL measurements
- **Setup**:
    - Organization is opted in
    - Events for last 4 hours are created
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

## Instance Reporting for Billing Account IDs

**tally-instances-billing-account-TC001 - Billing account IDs exclude old month data**

- **Description**: Verify that the billing account IDs endpoint only returns accounts with activity in the current month
- **Setup**:
  - Organization is opted in
  - Host with billing account ID created with last_seen date from 35 days ago (previous month)
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

- **Description**: Verify behavior when two instance events share the same billing account ID
- **Setup**:
  - Two events, same billing account ID, different instance IDs
- **Action**:
  - Call billing account IDs endpoint
- **Verification**:
  - Response reflects one logical account entry or two, per product rules
- **Expected Result**:
  - Duplicate account handling is stable and documented

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
  - Shared PAYG instances fixture; filter by a billing_account_id known to have current-month metered rows (TC004 billing account)
- **Action**:
  - Query instances for the product with month start 00:00:00 UTC through month end 23:59:59.999 UTC and billing_account_id set
- **Verification**:
  - At least one data row
  - Summed metered measurements > 0
- **Expected Result**:
  - Full-month same-month windows return expected PAYG instance rows for the fixture

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
