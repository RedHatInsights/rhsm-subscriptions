# Tally Processing Pipeline

**Functional Area:** Event ingestion, conflict resolution, data transformation, and summary message output

This test plan covers the core tally processing pipeline:
- Events IN from Kafka → Process/Tally → Store Snapshots → Publish Summaries OUT to Kafka

**Related Components:**
- Event normalization and validation
- Conflict resolution logic
- Product tag filtering
- Hypervisor relationship handling
- Snapshot persistence
- Tally summary message publishing

---

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
    - Add amendment event at oldest hour with distinct instance_id (value v1)
    - Add amendment event at newest hour with distinct instance_id (value v2)
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

**tally-hypervisor-TC007 - RHEL hypervisor with overlapping guest SLA appears once in instances**

- **Description**: Verify a hypervisor that inherited multiple guest SLA/usage combinations (multiple primary HYPERVISOR host tally buckets) appears only once in the instances report when SLA and USAGE are wildcarded, for both primary bucket search flag states
- **Setup**:
    - Organization is opted in
    - Feature flag `swatch.swatch-tally.enable-host-tally-bucket-primary-row-searches` is toggled on and off (parameterized)
    - HBI hypervisor host seeded for RHEL for x86
    - Two HBI guests mapped to that hypervisor with overlapping SLA and different usages:
        - Premium + Production
        - Premium + Development/Test
    - Nightly tally run for the organization
- **Action**:
    - GET instances report with category=hypervisor (SLA and usage wildcarded / default \_ANY)
- **Verification**:
    - Hypervisor subscription manager ID appears exactly once
    - Result count is the same with primary bucket searches enabled and disabled
- **Expected Result**:
    - Guest-derived overlapping SLA/usage buckets do not duplicate the hypervisor row when wildcard filters are used
    - Primary-row search behavior matches non-primary for hypervisor category queries

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

---

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
    - Group messages by snapshot_date timestamp to verify each hour is represented
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

