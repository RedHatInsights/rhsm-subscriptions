# Instance Reports API

**Functional Area:** REST API endpoints for querying instance/host data

This test plan covers the instance reports API endpoints that return host-level data with filtering, pagination, and sorting capabilities.

**Endpoints Covered:**
- `GET /api/rhsm-subscriptions/v1/instances/products/{productId}`
- `GET /api/rhsm-subscriptions/v1/instances/{instanceId}/guests`
- `GET /api/rhsm-subscriptions/v1/instances/billing_account_ids`

**Test Coverage:**
- Instance reporting for PAYG and Non-PAYG products
- Billing account ID filtering and listing
- Pagination and sorting
- Month-boundary handling for PAYG instances

---

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

**tally-instances-sorting-TC004 - Sort by display_name**

- **Description**: Verify instances can be sorted by display_name in ascending and descending order
- **Setup**:
    - Two instances with different display names (e.g., "nameA" and "nameZ")
- **Action**:
    - Query with sort=display_name and dir=ASC
    - Query with sort=display_name and dir=DESC
- **Verification**:
    - ASC query returns instances in alphabetical order (nameA first)
    - DESC query returns instances in reverse alphabetical order (nameZ first)
- **Expected Result**:
    - Display name sorting parameter correctly orders instances alphabetically

**tally-instances-sorting-TC005 - Sort by metric_id**

- **Description**: Verify instances can be sorted by metric_id value in ascending and descending order
- **Setup**:
    - Two instances with different metric values (e.g., 1.0 and 99.0 for the same metric)
- **Action**:
    - Query with sort=<metric_id> and dir=ASC
    - Query with sort=<metric_id> and dir=DESC
- **Verification**:
    - ASC query returns instances in numerical order (smallest value first)
    - DESC query returns instances in reverse numerical order (largest value first)
- **Expected Result**:
    - Metric value sorting parameter correctly orders instances numerically by metric measurement

**tally-instances-sorting-TC006 - Sort by category**

- **Description**: Verify instances can be sorted by category in ascending and descending order
- **Setup**:
    - Two instances with different categories (e.g., PHYSICAL and CLOUD)
- **Action**:
    - Query with sort=category and dir=ASC
    - Query with sort=category and dir=DESC
- **Verification**:
    - ASC and DESC queries return the same instances but in reversed category order
    - Reversing the ASC category list equals the DESC category list
- **Expected Result**:
    - Category sorting parameter correctly orders instances by report category

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
