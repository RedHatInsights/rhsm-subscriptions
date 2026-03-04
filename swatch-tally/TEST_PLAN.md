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

**tally-hypervisor-TC001 - Hypervisor without guests not in instances report**

- **Description**: Verify that a hypervisor with no guests does not appear in the instances report
- **Setup**:
    - Organization with baseline tally data
    - Nightly tally is performed
    - Hypervisor host is inserted with no guests and no buckets
- **Action**:
    - Perform tally for organization
    - Retrieve instances report for the day
- **Verification**:
    - Hypervisor's subscription manager ID is not in instances report data
    - Instances report does not include the hypervisor
- **Expected Result**:
    - Hypervisors without guests are excluded from instances reports
    - Only hosts with actual usage are visible

**tally-hypervisor-TC002 - Hypervisor without guests does not change daily total**

- **Description**: Verify that a hypervisor with no guests does not affect the daily total socket count
- **Setup**:
    - Organization with baseline usage (non-zero sockets)
    - Nightly tally is performed to establish baseline
    - Hypervisor host is inserted with no guests and no buckets
- **Action**:
    - Capture initial daily sockets total
    - Perform tally for organization
    - Capture new daily sockets total
- **Verification**:
    - Initial sockets total equals new sockets total
    - Hypervisor did not contribute to total
- **Expected Result**:
    - Hypervisors without guests do not affect tally totals
    - Only hosts with buckets contribute to aggregated metrics

## Data Persistence

**tally-persistence-TC001 - Tally report persistence across separate tally runs**

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

**tally-persistence-TC002 - Instance report persistence across separate tally runs**

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

**tally-report-granularity-TC002 - Daily granularity with partial filters**

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

**tally-report-granularity-TC003 - Hourly granularity with all filters**

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

**tally-report-granularity-TC004 - Invalid request without granularity**

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

## SLA Filtering

**tally-sla-filters-TC001 - SLA filter counts**

- **Description**: Verify that tally segregates and counts metrics by SLA level
- **Setup**:
    - Organization is opted in
    - Events created for each SLA type (PREMIUM, STANDARD, SELF_SUPPORT)
    - One event with no SLA
- **Action**:
    - Produce events to Kafka
    - Poll for tally summaries with HOURLY granularity
- **Verification**:
    - Sum of all SLA-filtered values equals total minus no-SLA value
    - Sum of SLA-filtered values plus no-SLA value equals total
    - Each SLA bucket contains correct metric values
- **Expected Result**:
    - Tally segregates metrics by SLA
    - No-SLA bucket is separate from defined SLA buckets
    - SLA-filtered totals sum to overall total

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
