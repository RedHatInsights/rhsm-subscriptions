# Tally Reports API

**Functional Area:** REST API endpoints for querying tally/usage reports

This test plan covers the tally reports API endpoints that return aggregated usage data at various granularities (hourly, daily, monthly, quarterly, yearly) with filtering capabilities.

**Endpoints Covered:**
- `GET /api/rhsm-subscriptions/v1/tally/products/{productId}/{metricId}`

**Test Coverage:**
- Report granularity and filtering (PAYG and Non-PAYG)
- Category-based data presence indicators (has_data field)
- Filter combinations (SLA, usage, billing provider, billing account)
- Metadata validation

---

## Report Granularity and Filtering (PAYG)

### Test Organization

The PAYG tally report filtering test cases are organized into two component test files:

- **TallyReportFiltersPaygTest.java** - Contains 22 PAYG test cases (`tally-report-filters-payg-TC***`; TC009, TC010, TC023 live in EdgeCaseTest):
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

**tally-report-filters-payg-TC001 - Daily granularity metadata with all filters**

- **Description**: Verify that tally report API accepts all filter parameters and correctly reflects them in response metadata
- **Setup**:
    - Organization is opted in
    - Daily time range is specified (3 days ago to 2 days ago)
- **Action**:
    - Request tally report with granularity=Daily, SLA=PREMIUM, usage=PRODUCTION, billing_provider=AWS, and billing_account_id
- **Verification**:
    - Response metadata is not null
    - Metadata granularity is DAILY
    - Metadata includes product tag matching request
    - Metadata includes metric ID matching request
    - Metadata SLA is PREMIUM
    - Metadata usage is PRODUCTION
    - Metadata billing provider is AWS
    - Metadata billing account ID matches request
- **Expected Result**:
    - API accepts all filter parameters and accurately reflects them in response metadata (this is a metadata verification test, not a data filtering test)

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

**tally-report-filters-payg-TC020 - Monthly granularity**

- **Description**: Verify that tally report API accepts monthly granularity and returns correct metadata
- **Setup**:
    - Organization is opted in
- **Action**:
    - Request tally report with granularity=Monthly, beginning and ending timestamps
- **Verification**:
    - Response metadata includes granularity=MONTHLY
    - Response metadata is not null
- **Expected Result**:
    - API accepts and processes monthly granularity queries

**tally-report-filters-payg-TC021 - Quarterly granularity**

- **Description**: Verify that tally report API accepts quarterly granularity and returns correct metadata
- **Setup**:
    - Organization is opted in
- **Action**:
    - Request tally report with granularity=Quarterly, beginning and ending timestamps
- **Verification**:
    - Response metadata includes granularity=QUARTERLY
    - Response metadata is not null
- **Expected Result**:
    - API accepts and processes quarterly granularity queries

**tally-report-filters-payg-TC022 - Yearly granularity**

- **Description**: Verify that tally report API accepts yearly granularity and returns correct metadata
- **Setup**:
    - Organization is opted in
- **Action**:
    - Request tally report with granularity=Yearly, beginning and ending timestamps
- **Verification**:
    - Response metadata includes granularity=YEARLY
    - Response metadata is not null
- **Expected Result**:
    - API accepts and processes yearly granularity queries

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
