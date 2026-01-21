# swatch-producer-azure

swatch-producer-azure is a service deployed within the Subscription Watch ecosystem that sends
billable usage data to Azure Marketplace for customer billing. The service acts as a bridge between
the SWATCH billing system and Azure Marketplace Metering Service, consuming billable usage aggregates
and submitting them to Azure for metering and billing purposes.

The service receives billable usage aggregates from the swatch-billable-usage service via Kafka,
enriches them with Azure-specific context information from swatch-contracts, and submits the usage
events to Azure Marketplace using the Azure Marketplace Metering API. It handles Azure-specific
requirements such as usage event formatting, authentication with Azure AD, request signing, and
error handling for duplicate submissions and throttling.

An incoming billable usage aggregate contains aggregated usage data for a specific organization,
product, metric, and time window. The service processes this data to create Azure usage events that
comply with Azure Marketplace requirements and submits them for billing.

An outgoing usage status message will contain:
  - The UUID of the original billable usage record
  - The status (SUCCESS or FAILED)
  - Error details if the submission failed
  - Timestamp of when the status was determined

In short, swatch-producer-azure is responsible for translating SWATCH billable usage data into
Azure-specific usage events and ensuring they are successfully submitted to Azure Marketplace,
while providing status feedback to swatch-billable-usage for tracking and reconciliation.

## Component/Flow Diagram
![Container diagram for Billing](../docs/container-billing.svg)

### Azure Billable Usage Aggregate Consumer
The Azure Billable Usage Aggregate Consumer is the main entry point into this service. It is a Kafka
consumer that consumes billable usage aggregate messages from the hourly aggregate topic
(platform.rhsm-subscriptions.billable-usage-hourly-aggregate). These aggregates are produced by
the swatch-billable-usage service after aggregating usage data into hourly windows.

#### Billable Usage Aggregate Processing
When a billable usage aggregate is received:
1. The service validates that the aggregate is for Azure billing provider.
2. It queries the swatch-contracts service to retrieve Azure usage context, which includes:
   - Azure resource ID (identifier for the customer's Azure subscription)
   - Plan ID (the specific marketplace plan the customer subscribed to)
   - Dimension ID (the Azure billing dimension name)
   - Azure subscription ID and tenant ID
3. The service validates the usage timestamp is within the acceptable Azure usage window (typically
   no older than a configured threshold to prevent rejected submissions).
4. The aggregate is converted into Azure UsageEvent format:
   - Resource URI is constructed from the Azure resource ID
   - Quantity is the aggregated usage value
   - Dimension is mapped from the metric ID
   - Plan ID identifies the marketplace offering
   - Effective start time is set to the aggregate window time
5. Each usage event is submitted individually to the Azure Marketplace Metering API.
6. The service processes the Azure response:
   - For successful submissions (Accepted status): produces a SUCCESS status message
   - For duplicate submissions (Duplicate status): treats as success since usage was already recorded
   - For rejected events (Expired, Error, ResourceNotFound): produces a FAILED status message with error details
   - For throttling: implements retry logic with exponential backoff

### Azure Marketplace Metering Integration
The service integrates with the Azure Marketplace Metering Service API to submit usage events. Key
aspects of this integration:
- **Authentication**: Azure uses OAuth 2.0 with Azure AD. The service manages Azure credentials
  (tenant ID, client ID, client secret) per marketplace offering and obtains access tokens for API calls.
- **Individual Submission**: Unlike AWS which supports batching, Azure requires individual usage
  event submissions. Each aggregate results in a separate API call.
- **Request Signing**: Usage event requests must include proper authorization headers with valid
  Azure AD bearer tokens.
- **Idempotency**: Azure uses the usage event ID for idempotency. The service generates deterministic
  IDs based on the billable usage UUID to prevent duplicate billing.
- **Dry Run Mode**: The service supports a dry run mode (ENABLE_AZURE_DRY_RUN) for testing without
  actually submitting usage to Azure Marketplace.

### Azure Usage Context Lookup
Before submitting usage to Azure, the service must determine the Azure-specific identifiers. It queries
the swatch-contracts service API endpoint to retrieve:
- **Azure Resource ID**: The customer's Azure Marketplace resource identifier
- **Plan ID**: The specific plan within the Azure Marketplace offer
- **Dimension ID**: The Azure billing dimension corresponding to the metric
- **Azure Subscription ID**: The customer's Azure subscription
- **Tenant ID**: The Azure AD tenant ID for authentication

This context lookup ensures that usage is properly attributed to the correct customer and plan
in Azure Marketplace.

### Usage Event Validation
The service performs several validations before submitting usage to Azure:
- **Timestamp Validation**: Ensures the usage timestamp is within the acceptable window
  (AZURE_MARKETPLACE_USAGE_WINDOW). Usage events that are too old will be rejected by Azure.
- **Credential Validation**: Verifies that Azure credentials are available and valid for the offering.
- **Subscription Status**: Checks that the subscription has not been recently terminated, as Azure
  will reject usage for terminated subscriptions.
- **Dimension Mapping**: Validates that the metric ID can be mapped to a valid Azure dimension.
- **Resource ID Validation**: Ensures the Azure resource ID is properly formatted and accessible.

### Status Reporting
After processing each usage aggregate, the service produces status messages to the billable usage
status topic (platform.rhsm-subscriptions.billable-usage.status). These status messages inform
swatch-billable-usage whether the usage was successfully billed:
- **SUCCESS**: Usage was accepted by Azure Marketplace (or was a duplicate of already-recorded usage)
  and will be billed to the customer
- **FAILED**: Usage submission failed due to errors (authentication failure, resource not found,
  subscription issues, expired timestamp, etc.)

Status messages include the original billable usage UUID for correlation and error details to aid
in troubleshooting.

### Azure Response Handling
Azure Marketplace returns different status codes for usage events:
- **Accepted**: Usage event was successfully recorded and will be billed
- **Duplicate**: Usage event was already submitted before (based on usage event ID). Treated as success.
- **Expired**: Usage timestamp is outside the acceptable window. Permanent failure.
- **ResourceNotFound**: The Azure resource ID is invalid or the subscription doesn't exist. Permanent failure.
- **ResourceNotAuthorized**: The credentials don't have permission to submit usage. Permanent failure.
- **InvalidDimension**: The dimension ID is not recognized for this plan. Permanent failure.
- **BadArgument**: The request format is invalid. Permanent failure.

### Error Handling and Retry Logic
The service implements comprehensive error handling:
- **Transient Errors**: Automatic retry with exponential backoff for temporary failures and throttling
- **Authentication Errors**: Token refresh and retry for expired or invalid tokens
- **Permanent Errors**: Immediate failure reporting for errors that won't succeed on retry
  (resource not found, expired timestamp, invalid dimension, terminated subscription)
- **Duplicate Detection**: Azure handles idempotency; duplicate submissions are treated as successful

### Azure AD Token Management
The service manages Azure AD access tokens for authentication:
- Tokens are obtained using the client credentials flow (client ID + client secret)
- Tokens are cached and reused until expiration
- Expired tokens are automatically refreshed before making API calls
- Each Azure offering/product may have different credentials

### Monitoring and Metrics
The service exposes several metrics for monitoring:
- `swatch_azure_marketplace_batch_accepted_total`: Count of usage events accepted by Azure
- `swatch_azure_marketplace_batch_rejected_total`: Count of usage events rejected by Azure
- `swatch_producer_metered_total`: Total usage metered by product, metric, and billing provider
