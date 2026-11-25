# Introduction

The **swatch-producer-azure** service consumes hourly billable usage aggregates, looks up Azure subscription context from **swatch-contracts**, submits usage events to Azure Marketplace, and publishes the resulting billable usage status.

This plan covers component tests for the Java component test framework.

**Purpose:** Verify Azure aggregate processing, dependency interactions, status reporting, and business metrics.

**Scope**:

* Consume the platform.rhsm-subscriptions.billable-usage-hourly-aggregate topic.
* Look up Azure usage context from Contracts.
* Request Azure OAuth tokens and submit usage events to Azure Marketplace.
* Publish status to the platform.rhsm-subscriptions.billable-usage.status topic.
* Exercise manual billable-usage submission through the service's internal business API.

**Assumptions:**

* Test aggregates use a product and metric configured for Azure unless a case tests filtering.
* Contracts, OAuth, and Marketplace are WireMock dependencies. No real Azure credentials or requests are used.
* Marketplace submission tests set ENABLE_AZURE_DRY_RUN=false.
* An Azure Marketplace HTTP 409 duplicate response represents an event already accepted and is treated as successful remittance.

**Constraints**:

* Only swatch-producer-azure runs as an application service. Kafka and WireMock provide the component boundaries.
* Azure end-to-end and deployed-environment tests are out of scope.
* Health/readiness routes, Kafka seek controls, and API documentation routes are outside this business-feature test plan.

# Test Strategy

Follow the case structure and level of detail in the swatch-contracts component test plan: one scenario per case, a clear setup and action, and results observable through Kafka, HTTP responses, WireMock, or business metrics.

- Publish aggregates to Kafka and correlate status records by aggregateId.
- Match WireMock requests to the specific Contracts path, tenant-specific OAuth path, and Azure /api/usageEvent path. Verify request parameters and bodies in mapping cases.
- Unless a case is testing that dependency or expects processing to stop before it, configure the normal successful dependency response: Contracts returns a valid Azure usage context; OAuth returns a valid token for each credential the producer will use; and Azure returns Accepted. Override the response under test. Cases with a valid aggregate after a skipped record also need these normal responses for that valid record.
- Use one input aggregate per case except when verifying that an invalid record does not prevent a following valid record from processing.
- Set startup-only options, including dry-run mode, manual submission, and Azure credential lists, in separate test service configurations.
- For no-output cases, publish the case input before a valid aggregate on the same partition and wait for the valid result before asserting no output for the first input. An immediately empty bridge cache is not proof of processing.
- The component-test framework manages service readiness; business metric samples are read through the framework's SwatchService helper.
- OAuth clients can cache tokens. Use fresh tenant clients or account for token reuse when checking OAuth request counts, and clear the WireMock request journal before per-case assertions.

Existing Java component tests cover accepted usage, a generic Contracts 404, timeout retries, malformed timestamp input, and a keyless aggregate. IQE coverage also exercises duplicate handling and invalid inputs; the Java cases below verify behavior at Kafka and WireMock boundaries.

# Endpoint Inventory

| Boundary | Endpoint or topic | Component-test coverage |
| --- | --- | --- |
| Azure producer business API | POST /api/swatch-producer-azure/internal/azure/billable_usage | azure-manual-TC001–TC002 |
| Contracts dependency | GET /api/swatch-contracts/internal/subscriptions/azureUsageContext | azure-contracts-TC001–TC007; azure-filter-TC009 |
| Azure OAuth dependency | POST {AZURE_OAUTH_TOKEN_URL} (tenant-specific /{tenantId}/oauth2/token) | azure-usage-TC001–TC008; azure-marketplace-TC001–TC006 |
| Azure Marketplace dependency | POST {AZURE_MARKETPLACE_BASE_URL}/api/usageEvent?api-version={AZURE_MARKETPLACE_API_VERSION} | azure-usage-TC001–TC008; azure-marketplace-TC001–TC006 |
| Kafka input | platform.rhsm-subscriptions.billable-usage-hourly-aggregate | Usage, Contracts, Marketplace, and filtering cases |
| Kafka status output | platform.rhsm-subscriptions.billable-usage.status | Usage, Contracts, Marketplace, filtering, and metrics cases |
| Manual submission output | platform.rhsm-subscriptions.billable-usage | azure-manual-TC001 |

# Test Cases

## Azure Usage Submission

**azure-usage-TC001 - Submit a valid Azure usage aggregate**

- **Description:** Verify a valid aggregate is mapped to an Azure usage event and its accepted status is published.
- **Setup:**
  - Use a configured Azure product and metric, such as rhel-for-x86-els-payg-addon / vCPUs.
  - Return HTTP 200 with an Azure usage context from Contracts, including the resource ID, plan ID, and a clientId matching the configured credential.
  - Return a valid OAuth token and Azure HTTP 200 Accepted response.
  - Set windowTimestamp to 23 hours before the current time.
  - Set ENABLE_AZURE_DRY_RUN=false.
- **Action:** Publish one valid aggregate to platform.rhsm-subscriptions.billable-usage-hourly-aggregate.
- **Verification:** Read the status by aggregateId and inspect the Contracts, OAuth, and Marketplace requests in WireMock.
- **Expected Result:**
  - One status is published with status=succeeded, errorCode=null, and billedOn populated.
  - The status preserves the aggregate ID and remittance UUIDs.
  - Contracts receives date, productId, orgId, sla, usage, and azureAccountId from the aggregate; azureAccountId is populated from billingAccountId.
  - Contracts receives the configured service-auth header.
  - OAuth uses the configured tenant, client credentials, client-credentials grant, and Marketplace resource.
  - Azure receives the resource ID and plan ID from Contracts, the aggregate quantity and effective time, the configured metric dimension and API version, and the OAuth bearer token.
  - When Contracts supplies clientId, Azure receives that optional field.
  - The status preserves the aggregate key and total value as well as the aggregate ID and remittance UUIDs.

**azure-usage-TC002 - Select the Azure credential identified by Contracts**

- **Description:** Verify the Contracts clientId selects the corresponding configured Azure credential.
- **Setup:**
  - Configure two Azure clients.
  - Return the second client's clientId in the Contracts context.
  - Stub that client's OAuth endpoint and Azure Marketplace to accept the event.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Inspect OAuth requests, the Azure authorization header, and the status by aggregateId.
- **Expected Result:**
  - Only the selected tenant's OAuth endpoint is called.
  - The selected bearer token is used for one Azure usage request.
  - The status is succeeded.

**azure-usage-TC003 - Fall back to another credential after authorization is denied**

- **Description:** Verify credential fallback when Contracts does not identify a configured client.
- **Setup:**
  - Configure two Azure clients and parameterize the Contracts context with an omitted or unmatched clientId.
  - Return HTTP 403 for the first client's Azure request and HTTP 200 Accepted for the second client's request.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Inspect the bearer token on each Azure request and read the status by aggregateId.
- **Expected Result:**
  - The first and second credentials each make one Azure request, in configured order.
  - The second request succeeds and the status is succeeded.

**azure-usage-TC004 - Report an OAuth token endpoint failure**

- **Description:** Verify a token acquisition failure is reported without submitting usage to Azure.
- **Setup:**
  - Return a matching clientId from Contracts.
  - Return an OAuth server error for the selected tenant.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count OAuth and Marketplace requests and read the status by aggregateId.
- **Expected Result:**
  - The token request is retried according to AZURE_SEND_RETRIES.
  - No Azure usage event is submitted.
  - The status is failed with errorCode=unknown.

**azure-usage-TC005 - Complete processing in dry-run mode**

- **Description:** Verify dry-run mode reports a successful aggregate without requesting an OAuth token or submitting an Azure usage event.
- **Setup:**
  - Return a valid Contracts context.
  - Set ENABLE_AZURE_DRY_RUN=true.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect OAuth and Marketplace requests.
- **Expected Result:**
  - The status is succeeded with no error code.
  - Contracts is called; OAuth and Azure Marketplace are not called.

**azure-usage-TC006 - Do not fall back when the selected credential is rejected**

- **Description:** Verify a Contracts-selected credential is retried by itself when Azure denies authorization.
- **Setup:**
  - Configure two Azure clients and return the first client's clientId from Contracts.
  - Return HTTP 403 for requests using the selected credential; configure the other credential to accept a request.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Marketplace requests by bearer token and read the status by aggregateId.
- **Expected Result:**
  - The selected credential receives four requests: one initial request and three retries.
  - The other credential receives no request.
  - The status is failed with errorCode=unknown.

**azure-usage-TC007 - Report failure after all fallback credentials are denied**

- **Description:** Verify all configured credentials are tried when Contracts does not identify a client, and the aggregate fails when none succeeds.
- **Setup:**
  - Configure two Azure clients and omit or use an unmatched clientId in the Contracts context.
  - Return HTTP 403 for every Azure request made with either credential.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Marketplace requests by bearer token and read the status by aggregateId.
- **Expected Result:**
  - Each credential receives four requests as the service retries the full fallback sequence.
  - The status is failed with errorCode=unknown.

**azure-usage-TC008 - Report failure when no Azure credentials are configured**

- **Description:** Verify the producer reports failure when its credential list is empty.
- **Setup:**
  - Start the service with an empty Azure Marketplace client list.
  - Return a valid Contracts context.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect OAuth and Marketplace request journals.
- **Expected Result:**
  - The status is failed with errorCode=unknown.
  - No OAuth or Azure usage request is made.

## Contracts Usage Context

**azure-contracts-TC001 - Report a missing Azure subscription**

- **Description:** Verify a generic Contracts 404 becomes a subscription-not-found status for an aggregate inside the configured usage window.
- **Setup:**
  - Set AZURE_MARKETPLACE_USAGE_WINDOW=96h.
  - Set the earliest snapshot date within the last 96 hours.
  - Return HTTP 404 from the Contracts usage-context endpoint.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect OAuth and Marketplace requests.
- **Expected Result:**
  - The status is failed with errorCode=subscription_not_found.
  - No OAuth token or Azure usage request is made.

**azure-contracts-TC002 - Report a recently terminated Azure subscription**

- **Description:** Verify the Contracts recently-terminated error maps to the subscription-terminated status.
- **Setup:** Return Contracts HTTP 404 with error code CONTRACTS1005.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect OAuth and Marketplace requests.
- **Expected Result:**
  - The status is failed with errorCode=subscription_terminated.
  - No OAuth token or Azure usage request is made.

**azure-contracts-TC003 - Mark an old aggregate inactive when no subscription is found**

- **Description:** Verify the producer's 96-hour snapshot window is applied when Contracts returns not found.
- **Setup:**
  - Set AZURE_MARKETPLACE_USAGE_WINDOW=96h.
  - Set the earliest snapshot date to 97 hours before the current time.
  - Return a generic Contracts HTTP 404.
- **Action:** Publish one Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect OAuth and Marketplace requests.
- **Expected Result:**
  - The status is failed with errorCode=inactive.
  - No OAuth token or Azure usage request is made.

**azure-contracts-TC004 - Treat a snapshot near the 96-hour cutoff as active**

- **Description:** Verify a snapshot close to the usage-window cutoff is still treated as active.
- **Setup:**
  - Set AZURE_MARKETPLACE_USAGE_WINDOW=96h.
  - Set the earliest snapshot date to the UTC start of the current hour minus 95 hours, one hour inside the cutoff.
  - Return a generic Contracts HTTP 404.
- **Action:** Publish one Azure aggregate.
- **Verification:** Read the status by aggregateId.
- **Expected Result:** The status is failed with errorCode=subscription_not_found, not inactive.

**azure-contracts-TC005 - Report a Contracts server error after retries are exhausted**

- **Description:** Verify a persistent Contracts server error is reported as a usage-context lookup failure after the configured retries.
- **Setup:** Return HTTP 500 for every Contracts usage-context request and set AZURE_USAGE_CONTEXT_LOOKUP_RETRIES=3.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Read the status by aggregateId and count Contracts, OAuth, and Marketplace requests.
- **Expected Result:**
  - The status is failed with errorCode=usage_context_lookup.
  - Contracts receives four requests: one initial request and three retries.
  - No OAuth token or Azure usage request is made.


**azure-contracts-TC006 - Report a Contracts transport failure**

- **Description:** Verify a Contracts connection failure before an HTTP response is received is reported as a usage-context lookup failure.
- **Setup:**
  - Use a supported Azure aggregate with a recent snapshot date.
  - Configure WireMock to reset the connection for the Contracts azureUsageContext request.
- **Action:** Publish one Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect the Contracts, OAuth, and Marketplace request journals.
- **Expected Result:**
  - The status is failed with errorCode=usage_context_lookup.
  - WireMock records the Contracts request.
  - No OAuth token or Azure usage request is made.

**azure-contracts-TC007 - Recover from a transient Contracts server error**

- **Description:** Verify usage processing continues when a Contracts retry succeeds after a transient server error.
- **Setup:**
  - Set AZURE_USAGE_CONTEXT_LOOKUP_RETRIES=3.
  - Return HTTP 500 for the first Contracts usage-context request and HTTP 200 with a valid Azure usage context for the next request. Include a clientId matching one configured credential.
  - Return a valid OAuth token and Azure HTTP 200 Accepted response.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Contracts, OAuth, and Marketplace requests and read the status by aggregateId.
- **Expected Result:**
  - Contracts receives two requests: the initial request and one retry.
  - OAuth and Azure Marketplace each receive one request.
  - The status is succeeded with errorCode=null.

## Azure Marketplace Responses

**azure-marketplace-TC001 - Recover from a transient Azure server error**

- **Description:** Verify a later retry succeeds after a transient Marketplace server error.
- **Setup:**
  - Return a Contracts context with a clientId matching one configured credential.
  - Return Azure HTTP 500 for the first usage request and HTTP 200 Accepted for the next.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Azure usage-event requests and read the status by aggregateId.
- **Expected Result:**
  - Azure receives two usage requests.
  - The status is succeeded with no error code.

**azure-marketplace-TC002 - Fail after Azure request timeouts exhaust retries**

- **Description:** Verify a request timeout is retried and reported as a failed remittance after the configured attempts.
- **Setup:**
  - Return a matching clientId from Contracts.
  - Delay every Azure response beyond the configured read timeout.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Azure usage-event requests and read the status by aggregateId.
- **Expected Result:**
  - Azure receives four usage requests: one initial request and three retries.
  - The status is failed with errorCode=unknown.

**azure-marketplace-TC003 - Treat a duplicate Azure event as successful**

- **Description:** Verify a duplicate response is idempotent when Azure reports an HTTP 409 conflict.
- **Setup:** Return HTTP 409 from the Azure usage-event endpoint. The producer classifies it by status code and does not inspect the response body.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Azure usage-event requests and read the status by aggregateId.
- **Expected Result:**
  - Azure receives one usage request and no retry occurs.
  - The status is succeeded with no error code.

**azure-marketplace-TC004 - Report a Marketplace rate limit**

- **Description:** Verify a rate-limit response is retried and then reported as a failed remittance.
- **Setup:**
  - Return a Contracts context with a clientId matching one configured credential.
  - Return HTTP 429 for every Azure usage request.
- **Action:** Publish one valid Azure aggregate.
- **Verification:** Count Azure usage-event requests and read the status by aggregateId.
- **Expected Result:**
  - Azure receives four usage requests: one initial request and three retries.
  - The status is failed with errorCode=unknown.

**azure-marketplace-TC005 - Handle Azure HTTP 400 for an out-of-window event time**

- **Description:** Verify the producer forwards an out-of-window effective time and publishes the status produced by its Azure HTTP 400 handling.
- **Setup:**
  - Return a valid Contracts context and a matching configured credential.
  - Parameterize the aggregate effective time as 25 hours in the past and one hour in the future.
  - Return HTTP 400 for the usage-event request; the producer branches on the status code and does not interpret the error body.
- **Action:** Publish one Azure aggregate.
- **Verification:** Inspect the submitted effectiveStartTime, count Azure requests, and read the status by aggregateId.
- **Expected Result:**
  - Azure receives the effective time from the aggregate.
  - Azure receives one request; HTTP 400 is converted to a non-exception response and is not retried.
  - The status is succeeded with errorCode=null because the consumer publishes success after any non-exception response.

**azure-marketplace-TC006 - Handle Azure HTTP 400 for a non-positive quantity**

- **Description:** Verify the producer sends a non-positive quantity and publishes the status produced by its Azure HTTP 400 handling.
- **Setup:**
  - Return a valid Contracts context and a matching configured credential.
  - Set totalValue to zero.
  - Return HTTP 400 for the usage-event request; the producer branches on the status code and does not interpret the error body.
- **Action:** Publish one Azure aggregate.
- **Verification:** Inspect the submitted quantity, count Azure requests, and read the status by aggregateId.
- **Expected Result:**
  - Azure receives quantity=0.
  - Azure receives one request; HTTP 400 is converted to a non-exception response and is not retried.
  - The status is succeeded with errorCode=null because the consumer publishes success after any non-exception response.

## Aggregate Filtering and Input Validation

**azure-filter-TC001 - Ignore aggregates for other billing providers**

- **Description:** Verify an AWS aggregate on the shared topic is ignored by the Azure producer.
- **Setup:**
  - Create an aggregate whose billingProvider is aws.
  - Stub a successful Contracts, OAuth, and Marketplace flow for a following valid Azure aggregate.
- **Action:** Publish the AWS aggregate, followed by one valid Azure aggregate on the same partition.
- **Verification:** Read the Azure aggregate's status and inspect Contracts, OAuth, and Marketplace request journals.
- **Expected Result:**
  - No status or outbound HTTP request is produced for the AWS aggregate.
  - The following Azure aggregate produces one succeeded status and one successful Azure usage request.

**azure-filter-TC002 - Report a metric without an Azure dimension**

- **Description:** Verify a configured metric without an Azure dimension is reported as unsupported.
- **Setup:** Use rhel-for-x86-els-payg with its configured vCPUs metric, which has no Azure dimension.
- **Action:** Publish one Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect outbound request journals.
- **Expected Result:**
  - The status is failed with errorCode=unsupported_metric.
  - No Contracts, OAuth, or Azure Marketplace request is made.

**azure-filter-TC003 - Report a metric not configured for the product**

- **Description:** Verify a globally known metric that is not configured for the selected product is reported as unsupported.
- **Setup:** Use productId=rosa with the globally known metricId=Storage-gibibyte-months, which is not in that product's metric list.
- **Action:** Publish one Azure aggregate.
- **Verification:** Read the status by aggregateId and inspect outbound request journals.
- **Expected Result:**
  - The status is failed with errorCode=unsupported_metric.
  - No Contracts, OAuth, or Azure Marketplace request is made.

**azure-filter-TC004 - Ignore malformed timestamp input and process the next aggregate**

- **Description:** Verify a Kafka deserialization failure does not stop processing the next valid aggregate.
- **Setup:**
  - Set USAGE_AGGREGATE_FAIL_ON_DESER_FAILURE=false so malformed records are ignored.
  - Prepare a malformed aggregate payload with an invalid windowTimestamp.
- **Action:** Publish the malformed payload, followed by one valid Azure aggregate with a unique aggregateId.
- **Verification:** Read the valid aggregate's status and inspect Contracts and Marketplace request journals.
- **Expected Result:**
  - The malformed payload produces no status or outbound HTTP request.
  - The valid aggregate produces one succeeded status and one Azure usage request.

**azure-filter-TC005 - Ignore an aggregate without an aggregate key**

- **Description:** Verify a keyless aggregate is ignored without stopping the consumer.
- **Setup:** Prepare valid JSON for an aggregate whose aggregateKey is null.
- **Action:** Publish the keyless aggregate, followed by one valid Azure aggregate with a unique aggregateId.
- **Verification:** Read the valid aggregate's status and inspect Contracts and Marketplace request journals.
- **Expected Result:**
  - The keyless aggregate produces no status or outbound HTTP request.
  - The valid aggregate produces one succeeded status and one Azure usage request.

**azure-filter-TC006 - Ignore a null Kafka value**

- **Description:** Verify a null Kafka value is ignored without stopping the consumer.
- **Setup:** Prepare one valid Azure aggregate with a unique aggregateId.
- **Action:** Publish a null value, followed by the valid aggregate.
- **Verification:** Read the valid aggregate's status and inspect Contracts and Marketplace request journals.
- **Expected Result:**
  - The null value produces no status or outbound HTTP request.
  - The valid aggregate produces one succeeded status and one Azure usage request.

**azure-filter-TC007 - Ignore an aggregate with no metric ID**

- **Description:** Verify an aggregate without a metric ID produces no status and does not prevent the next valid aggregate from processing.
- **Setup:**
  - Create an aggregate for a supported Azure product with metricId omitted.
  - Stub a successful Contracts, OAuth, and Marketplace flow for the following valid aggregate.
- **Action:** Publish the aggregate, followed by one valid Azure aggregate with a unique aggregateId.
- **Verification:** Read the valid aggregate's status and inspect the Contracts, OAuth, and Marketplace request journals.
- **Expected Result:**
  - The aggregate without a metric ID produces no status or outbound HTTP request.
  - The following valid aggregate produces one succeeded status and one successful Azure usage request.

**azure-filter-TC008 - Ignore an aggregate with an unknown metric ID**

- **Description:** Verify an unknown metric ID produces no status and does not prevent the next valid aggregate from processing.
- **Setup:**
  - Create an aggregate for a supported Azure product with an unrecognized metricId.
  - Stub a successful Contracts, OAuth, and Marketplace flow for the following valid aggregate.
- **Action:** Publish the aggregate, followed by one valid Azure aggregate with a unique aggregateId.
- **Verification:** Read the valid aggregate's status and inspect the Contracts, OAuth, and Marketplace request journals.
- **Expected Result:**
  - The aggregate with an unknown metric ID produces no status or outbound HTTP request.
  - The following valid aggregate produces one succeeded status and one successful Azure usage request.

**azure-filter-TC009 - Report an aggregate without a window timestamp**

- **Description:** Verify a missing aggregate timestamp is reported as a Contracts usage-context lookup failure.
- **Setup:**
  - Create an Azure aggregate without windowTimestamp and with a recent snapshot date.
  - Return HTTP 400 from Contracts when the usage-context request has no date query parameter.
- **Action:** Publish one aggregate.
- **Verification:** Inspect the Contracts request and read the status by aggregateId.
- **Expected Result:**
  - The Contracts request omits the date query parameter.
  - The status is failed with errorCode=usage_context_lookup.
  - No OAuth token or Azure usage request is made.

## Manual Billable Usage Submission

**azure-manual-TC001 - Submit billable usage when manual submission is enabled**

- **Description:** Verify the manual submission endpoint queues a valid billable usage message when enabled.
- **Setup:**
  - Set AZURE_MANUAL_SUBMISSION_ENABLED=true.
  - Subscribe the Kafka Bridge to platform.rhsm-subscriptions.billable-usage.
- **Action:** POST a valid BillableUsage payload to /api/swatch-producer-azure/internal/azure/billable_usage.
- **Verification:** Inspect the HTTP response and the manual submission topic.
- **Expected Result:**
  - HTTP 202 is returned.
  - One message equivalent to the submitted payload is published.

**azure-manual-TC002 - Reject manual submission when it is disabled**

- **Description:** Verify manual billable usage is rejected when the feature is disabled.
- **Setup:** Set AZURE_MANUAL_SUBMISSION_ENABLED=false.
- **Action:** POST a valid BillableUsage payload to /api/swatch-producer-azure/internal/azure/billable_usage.
- **Verification:** Inspect the HTTP response and the manual submission topic.
- **Expected Result:**
  - HTTP 403 is returned with error code AZURE1005.
  - No message is published.

## Status and Metrics

**azure-status-TC001 - Record metrics for an accepted aggregate**

- **Description:** Verify accepted usage and metered-total counters increase for a successful aggregate.
- **Setup:**
  - Stub a successful Contracts, OAuth, and Marketplace flow.
  - Record the current accepted, rejected, and metered-total counter values.
- **Action:** Publish one valid aggregate.
- **Verification:** Read the status and matching tagged business-metric samples through the component-test framework's metrics helper.
- **Expected Result:**
  - The status is succeeded.
  - swatch_azure_marketplace_batch_accepted_total increases once; swatch_azure_marketplace_batch_rejected_total does not increase.
  - swatch_producer_metered_total increases by totalValue / billingFactor.
  - Metric tags include the product, metric ID, billing_provider=azure, and status=succeeded.

**azure-status-TC002 - Record metrics after Azure retries are exhausted**

- **Description:** Verify rejected usage and metered-total counters after every Marketplace retry fails.
- **Setup:**
  - Return a matching Contracts context.
  - Return Azure HTTP 500 for every usage-event request.
  - Record the current accepted, rejected, and metered-total counter values.
- **Action:** Publish one valid aggregate.
- **Verification:** Read the status and matching tagged business-metric samples through the component-test framework's metrics helper.
- **Expected Result:**
  - The status is failed with errorCode=unknown.
  - swatch_azure_marketplace_batch_rejected_total increases once after four Azure requests; swatch_azure_marketplace_batch_accepted_total does not increase.
  - swatch_producer_metered_total increases by totalValue / billingFactor with status=failed and error_code=unknown.

# Useful Links

- [Component Testing Framework guide](../docs/component-tests.md)
- [Azure producer component tests](ct/java/tests)
- [Contracts component test plan](../swatch-contracts/COMPONENT_TEST_PLAN.md)
- [Contracts API OpenAPI](../swatch-contracts/src/main/resources/META-INF/openapi.yaml)
- [Azure Marketplace API specification](../clients/quarkus/azure-marketplace-client/azure-marketplace-api-spec.yaml)
- [Azure Marketplace metered billing API documentation](https://learn.microsoft.com/en-us/partner-center/marketplace/marketplace-metering-service-apis)
- [Azure Marketplace authentication documentation](https://learn.microsoft.com/en-us/partner-center/marketplace-offers/marketplace-metering-service-authentication)
