# swatch-billable-usage

swatch-billable-usage is a service deployed within the Subscription Watch ecosystem that
processes tally summaries, determines billable usage based on contract coverage, and produces
billing events for consumption by downstream billing systems.

The service receives tally summaries from the tally service, retrieves contract information
from the swatch-contracts service to determine what usage is covered by contracts, and calculates
the billable amount by subtracting contract coverage from total usage. It maintains remittance
records to track what has been billed, ensures only new usage is billed, and produces billable
usage messages to downstream SWATCH vendor-specific producer services. Currently, there are two
vendor producers:
- **swatch-producer-aws**: Sends billable usage to AWS Marketplace
- **swatch-producer-azure**: Sends billable usage to Azure Marketplace

An incoming tally summary contains usage measurements for a specific product, metric, organization,
and billing account within a given time period. The billable usage service processes this data to
determine what portion of the usage should be billed after applying contract coverage.

An outgoing billable usage message will contain:
  - Billing metadata (organization ID, billing account ID, billing provider, product ID, metric ID)
  - The billable value after applying contract coverage and billing factors
  - Remittance tracking information (UUID, status, timestamp)
  - The accumulation period for the usage

In short, once a billable usage event is sent, it represents the exact amount that should be billed
to the customer for the given period, with all contract coverage and previous remittances already
accounted for.

## Component/Flow Diagram
![Container diagram for Billing](../docs/container-billing.svg)

### Tally Summary Consumer
The Tally Summary Consumer is the main entry point into this service. It is a Kafka consumer that
consumes tally summary messages from the tally topic (platform.rhsm-subscriptions.tally). The tally
service emits these messages whenever usage is tallied for a product and metric.

#### Tally Summary Processing
When a tally summary message is received:
1. The tally summary is mapped into one or more billable usage records based on the products,
   metrics, and billing dimensions present in the summary.
2. For each billable usage record, the service queries the contracts service to determine if
   there is contract coverage that applies to the usage.
3. The service calculates the applicable usage by subtracting the contract coverage from the
   total measured usage.
4. The service queries the database to determine how much usage has already been remitted for
   the same organization, product, metric, billing account, and accumulation period.
5. The billable amount is calculated by subtracting the already-remitted amount from the
   applicable usage, converting to billing units, and rounding up to the nearest integer.
6. If there is billable usage to remit:
   - A remittance record is created in the database with status PENDING or GRATIS (if contract
     coverage is marked as gratis).
   - The billable usage is produced to the billable usage Kafka topic
     (platform.rhsm-subscriptions.billable-usage) for consumption by vendor-specific producer
     services (swatch-producer-aws, swatch-producer-azure).

### Contract Coverage Handling
The service integrates with swatch-contracts to determine what usage is covered by customer
contracts. If a product is configured for contract enablement:
- The service queries the contracts service with the usage details (organization, product,
  metric, billing provider, etc.)
- The contracts service returns the amount of usage covered by contracts and whether it is
  marked as gratis (free).
- Usage covered by contracts is not billed unless it exceeds the contracted amount.
- If coverage is marked as gratis, a remittance record is still created but with GRATIS status,
  and no billing event is sent.

### Remittance Tracking
The billable_usage_remittance table tracks what usage has been sent for billing to prevent
double-billing and to handle out-of-order message processing.

A remittance record contains the following data:
* **uuid**:
  * Unique identifier for the remittance record.
* **org_id**:
  * The organization being billed.
* **product_id**:
  * The product for which usage is being billed.
* **metric_id**:
  * The specific metric being billed (e.g., vCPUs, cores, instances).
* **accumulation_period**:
  * The billing period (typically a month in YYYY-MM format).
* **billing_provider**:
  * The cloud provider or billing platform (e.g., aws, azure, red hat).
* **billing_account_id**:
  * The customer's billing account identifier.
* **remitted_pending_value**:
  * The amount of usage (in metric units) that was remitted in this record.
* **remittance_pending_date**:
  * The timestamp when this remittance was created.
* **status**:
  * The status of the remittance (PENDING, SUCCESS, FAILED, GRATIS).
* **error_code**:
  * If the remittance failed, the error code indicating why.
* **billed_on**:
  * The timestamp when billing was confirmed (updated based on status messages).
* **tally_id**:
  * Reference to the tally snapshot that generated this billable usage.

### Billable Usage Aggregation
The service includes a Kafka Streams topology that aggregates billable usage messages into
hourly windows. This aggregation:
- Groups billable usage by key dimensions (organization, product, metric, billing provider,
  billing account, SLA, usage type).
- Windows the data into hourly time windows with a grace period for late-arriving messages.
- Suppresses intermediate aggregates and only emits the final aggregate when the window closes.
- Produces aggregated usage to the hourly aggregate topic
  (platform.rhsm-subscriptions.billable-usage-hourly-aggregate).

A scheduled job (sync) triggers a flush of the Kafka Streams state stores hourly to ensure
aggregates are produced even for low-volume streams.

### Billable Usage Status Consumer
The service consumes status messages from the billable usage status topic
(platform.rhsm-subscriptions.billable-usage.status). These messages are produced by the vendor
producer services (swatch-producer-aws, swatch-producer-azure) to indicate whether a billable
usage message was successfully processed or failed.

When a status message is received:
1. The service looks up the remittance record by UUID.
2. The status is updated to SUCCESS or FAILED based on the message.
3. If successful, the billed_on timestamp is recorded.
4. If failed, an error code is recorded.

### Remittance Purge Task
A scheduled job (purge-remittances) runs daily to clean up old remittance records and produce
purge tasks to enable other services to clean up related data. The job produces messages to the
remittances purge task topic (platform.rhsm-subscriptions.remittances-purge-task) containing
the cutoff date for data retention.

### Billing Factor Calculation
The service applies billing factors when converting from metric units to billing units. For
example, if a product bills in "cores" but measures in "vCPUs", and the billing factor is 2,
then 2 vCPUs equals 1 billable core. Billing factors are defined in the swatch-product-configuration
library and are applied consistently across usage calculations.

### Outgoing Billable Usage Messages
An outgoing billable usage message will have a status of PENDING or GRATIS. The message includes
all the data required by the vendor producer services to bill the customer through their respective
cloud marketplaces (AWS Marketplace, Azure Marketplace). The message should never require further
calculation or normalization. The billing value is always a non-negative integer in billing units,
and the remittance UUID allows for status tracking.