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

**tally-report-granularity-TC001 - Daily granularity with all filters**

- **Description**: Verify that tally report API returns daily granularity data with all filter parameters
- **Setup**:
    - Organization is opted in
    - Daily time range is specified (3 days ago to 2 days ago)
- **Action**:
    - Request tally report with granularity=Daily, SLA, usage, billing provider, and billing account ID
- **Verification**:
    - Response data is not null
    - Response metadata matches requested filters
    - Metadata granularity is DAILY
    - Metadata includes product, metric, SLA, usage, billing provider, and billing account ID
    - Data count matches metadata count
- **Expected Result**:
    - API returns daily tally data with all specified filters
    - Metadata accurately reflects the request parameters

**tally-report-granularity-TC002 - Daily granularity filtered by SLA**

- **Description**: Verify that tally report API filters data by SLA parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with SLA=PREMIUM
    - Event 2 created with SLA=STANDARD
    - Hourly tally is performed
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity=Daily and sla=STANDARD
- **Verification**:
    - Response data contains only Event 2's data
    - Response metadata includes SLA=STANDARD
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by SLA parameter
    - Only data matching the specified SLA is returned

**tally-report-granularity-TC003 - Daily granularity filtered by usage**

- **Description**: Verify that tally report API filters data by usage parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with usage=PRODUCTION
    - Event 2 created with usage=DEVELOPMENT
    - Hourly tally is performed
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity=Daily and usage=DEVELOPMENT
- **Verification**:
    - Response data contains only Event 2's data
    - Response metadata includes usage=DEVELOPMENT
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by usage parameter
    - Only data matching the specified usage is returned

**tally-report-granularity-TC004 - Daily granularity filtered by billing provider**

- **Description**: Verify that tally report API filters data by billing provider parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_provider=AWS
    - Event 2 created with billing_provider=AZURE
    - Hourly tally is performed
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity=Daily and billing_provider=AZURE
- **Verification**:
    - Response data contains only Event 2's data
    - Response metadata includes billing_provider=AZURE
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by billing provider parameter
    - Only data matching the specified billing provider is returned

**tally-report-granularity-TC005 - Daily granularity filtered by billing account ID**

- **Description**: Verify that tally report API filters data by billing account ID parameter
- **Setup**:
    - Organization is opted in
    - Event 1 created with billing_account_id=account1
    - Event 2 created with billing_account_id=account2
    - Hourly tally is performed
    - Daily time range is specified
- **Action**:
    - Request tally report with granularity=Daily and billing_account_id=account2
- **Verification**:
    - Response data contains only Event 2's data
    - Response metadata includes billing_account_id=account2
    - Response does not include Event 1's data
- **Expected Result**:
    - API filters tally data by billing account ID parameter
    - Only data matching the specified billing account ID is returned

**tally-report-granularity-TC006 - Daily granularity with partial filters**

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

**tally-report-granularity-TC007 - Hourly granularity with all filters**

- **Description**: Verify that tally report API returns hourly granularity data with all filter parameters
- **Setup**:
    - Organization is opted in
    - Hourly time range is specified (4 hours ago to 1 hour from now)
- **Action**:
    - Request tally report with granularity=Hourly and all filters
- **Verification**:
    - Response data is not null
    - Metadata granularity is HOURLY
    - All filter parameters are reflected in metadata
- **Expected Result**:
    - API returns hourly tally data
    - Hourly granularity data includes all applied filters

**tally-report-granularity-TC008 - Invalid request without granularity**

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

**tally-report-granularity-TC009 - Daily report measurements update when same host changes billing account ID**

- **Description**: Verify that the daily tally report correctly reflects measurements when the same instance reports under different billing account IDs across separate hourly tally syncs
- **Setup**:
    - Organization is opted in
    - A single instance ID is used for both events
    - Two distinct billing account IDs
    - Time range covers the current day (daily granularity)
- **Action**:
    - Produce an event with the first billing account ID (value 5.0) and run hourly tally
    - Verify the daily report reflects the value for the first billing account and the total
    - Produce a second event for the same instance with a different billing account ID (value 8.0) and run hourly tally again
- **Verification**:
    - After first event: daily report shows 5.0 for the first billing account and 5.0 total
    - After second event: daily report shows 5.0 for the first billing account, 8.0 for the second, and 13.0 total
- **Expected Result**:
    - Hourly tally produces daily snapshots separated by billing account ID
    - Each billing account retains its own measurement value
    - Total report reflects the sum of all billing account values

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

## Instance Reporting

**tally-instances-TC001 - Billing account IDs exclude old month data**

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

**tally-instances-TC002 - Multiple billing account IDs returned**

- **Description**: Verify that the billing account IDs endpoint returns all active billing accounts
- **Setup**:
    - Organization is opted in
    - Two hosts created with different billing account IDs
    - Both hosts associated with AWS billing provider
- **Action**:
    - Call get billing account IDs endpoint
- **Verification**:
    - Response contains exactly 2 billing account entries
    - Both billing account IDs are present in response
    - Each entry has correct org_id, product_tag, and billing_provider fields
    - Billing provider is set to "aws"
- **Expected Result**:
    - All active billing accounts are returned
    - Response structure includes all required fields
    - Multiple billing accounts are properly differentiated

**tally-instances-TC003 - PAYG instances metered by month boundary**

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
