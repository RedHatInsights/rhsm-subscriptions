# swatch-producer-azure Component Test Plan

**Status:** Draft for SWATCH team review
**Test level:** Java component tests (`swatch-producer-azure/ct`)
**Last updated:** 2026-11-23

## Purpose and scope

This plan defines component-level coverage for `swatch-producer-azure`, which consumes Azure billable-usage aggregates, resolves Azure usage context, submits a single usage event to Azure Marketplace, and emits a result to the billable-usage status topic.

The component tests run one live `swatch-producer-azure` service. Kafka is the message transport used by the test framework; WireMock stubs the Contracts API, Azure OAuth token endpoint, and Azure Marketplace. Tests must not call stage or production services.

### Phase 1: producer flow from the team overview

- Consume `platform.rhsm-subscriptions.billable-usage-hourly-aggregate`.
- Request Azure usage context from `swatch-contracts`.
- Obtain OAuth credentials and submit one event to Azure Marketplace.
- Verify the final message on `platform.rhsm-subscriptions.billable-usage.status` and captured HTTP requests.
- Cover filtering, failure classification, retries, and duplicate handling.

### Phase 2: service HTTP control endpoints

The service OpenAPI also exposes a manual billable-usage endpoint and two Kafka seek endpoints. These are listed in the endpoint inventory and have proposed API cases below. They are outside the Kafka-to-Azure flow in the supplied overview; confirm with the team whether to include them in this component-test plan.

### Out of scope

- Testing Contracts service behavior itself or running multiple live SWATCH services.
- Live Azure credentials, live Azure Marketplace calls, stage/prod verification, load testing, and end-to-end billing behavior in `swatch-billable-usage`.
- Azure Marketplace `GET /api/usageEvents` and `POST /api/batchUsageEvent`: present in the client specification, but not called by this service, which submits one event at a time.
- Runtime health, metrics, OpenAPI, and Swagger UI endpoints.

## Endpoint and message inventory

| Boundary | Method / path | Contract exercised | Planned coverage |
|---|---|---|---|
| Kafka input | `platform.rhsm-subscriptions.billable-usage-hourly-aggregate` | Hourly aggregate carrying product, metric, provider, billing account, timestamp, quantity, and correlation/remittance data. | Happy path, filtering, malformed input, context errors, Marketplace outcomes. |
| Contracts API | `GET /api/swatch-contracts/internal/subscriptions/azureUsageContext` | `date` and `productId` are required. The producer also supplies `orgId`, `sla`, `usage`, and `azureAccountId` (from `billingAccountId`) when available. | Verify query mapping and 200 context; cover 404, terminated subscription, stale usage, and transient failure. |
| Azure AD OAuth | `POST https://login.microsoftonline.com/{tenantId}/oauth2/token` | Client-credentials grant with the configured Marketplace `resource`; `{tenantId}` is substituted per credential set. | Verify token request and Bearer authorization; test credential fallback. |
| Azure Marketplace | `POST https://marketplaceapi.microsoft.com/api/usageEvent?api-version=2018-08-31` | One `UsageEvent` per aggregate. Required fields: `quantity`, `dimension`, `effectiveStartTime`, `planId`; producer also sets `resourceId` and, when present, `clientId`. | Verify request mapping, Accepted/Duplicate outcomes, retry/failure behavior, and agreed error statuses. |
| Kafka output | `platform.rhsm-subscriptions.billable-usage.status` | Updated aggregate with `SUCCEEDED` or `FAILED`, error code when failed, and original identifiers retained. | Assert the final status and correlation fields for each applicable scenario. |
| Manual submission API (Phase 2) | `POST /api/swatch-producer-azure/internal/azure/billable_usage` | Queues a `BillableUsage` to `platform.rhsm-subscriptions.billable-usage` when `AZURE_MANUAL_SUBMISSION_ENABLED=true`; otherwise returns `403`. | Proposed cases `producer-azure-http-TC001` and `TC002`. |
| Kafka seek API (Phase 2) | `POST /api/swatch-producer-azure/internal/kafka_seek_position?position={enum}` | Supports `beginning` and `end`; successful response is `202`. | Proposed one case for each position: `producer-azure-http-TC003` and `TC004`. |
| Kafka seek API (Phase 2) | `POST /api/swatch-producer-azure/internal/kafka_seek_timestamp?timestamp={OffsetDateTime}` | Seeks to the supplied timestamp; successful response is `202`. | Proposed valid timestamp case `producer-azure-http-TC005`; invalid-input response needs contract confirmation (`TC006`). |

### Contract details to use in fixtures

- The current Contracts OpenAPI names the billing-account query parameter `azureAccountId`; an older example in the supplied overview uses `accountNumber=None`. The tests should follow the checked-in API contract and assert `azureAccountId`.
- The current `AzureUsageContext` schema uses `azureResourceId`, `planId`, and optional `clientId`. The producer uses `clientId` to select a credential when present. The overview's `azureOfferId` example is not used to construct the usage event.
- For a known product and metric, assert the event carries the original aggregate quantity and window timestamp, the configured Azure dimension, and resource/plan identifiers from the Contracts response. Use `api-version=2018-08-31` and a Bearer token.
- Configure `ENABLE_AZURE_DRY_RUN=false` for Marketplace interaction tests. Stub all external HTTP calls through WireMock.

## Phase 1 test cases

Use the IDs below verbatim in `@TestPlanName`. Each component test should have one action (one `When`) and assert outcomes through Kafka messages and recorded WireMock requests. Logs may assist diagnosis but are not the acceptance signal.

| Test plan ID | Scenario and setup | Expected result | Current coverage |
|---|---|---|---|
| `producer-azure-flow-TC001` | Publish a supported Azure aggregate; Contracts returns context; OAuth succeeds; Marketplace returns HTTP `200` with `Accepted`. | Exactly one matching status message with `SUCCEEDED` and no error code. | Existing `SimpleAzureComponentTest.testValidAzureUsageMessages`; currently lacks `@TestPlanName`. |
| `producer-azure-flow-TC002` | Publish a known aggregate and return distinct Contracts context values; capture all external requests. | Contracts query matches aggregate date/product/org/SLA/usage/billing account. Usage event has matching resource ID, plan ID, quantity, timestamp, Azure dimension, and optional client ID; request uses API version and Bearer authorization. | Partially covered by the same existing happy-path test; detailed query/header/body assertions are still needed. |
| `producer-azure-credentials-TC001` | Configure two credential sets. Omit `clientId` from the context to exercise fallback; first Marketplace submission is rejected with `403`, second succeeds. | The producer tries the next credential and emits one final `SUCCEEDED` status with no error code. It does not emit an intermediate `FAILED` status for the first attempt. Verify the token and usage requests for both credentials. | No component-test coverage. Existing unit tests exercise credential fallback with mocked API clients, not the OAuth/HTTP flow end to end. |
| `producer-azure-marketplace-TC001` | Return HTTP `429` from the usage-event endpoint for each configured send attempt. | Retry according to `AZURE_SEND_RETRIES`, then emit one `FAILED` status. The supplied overview expects `market_rate_limit`; current Azure code emits `UNKNOWN` after exhausted retries. Resolve this mapping before treating the requested code as an implementation-ready assertion. | No rate-limit component-test coverage. |
| `producer-azure-marketplace-TC002` | Return HTTP `409 Conflict` for a repeated usage event. | Treat the duplicate as successful and emit `SUCCEEDED` with no error code. Do not publish a `Duplicate` value as the billable-usage status. | No component-test coverage. The service maps `409` to Azure `Duplicate`; the producer README says duplicates are successful. |
| `producer-azure-marketplace-TC003` | Make the Marketplace request time out past the configured read timeout on every send attempt. | Retry the initial request plus the configured retries, then emit one `FAILED` status with `UNKNOWN`. | Existing `ApiMarketplaceAzureComponentTest.testAzureMarketplaceTakesTooLongToRespond` checks four requests and failure, but lacks `@TestPlanName` and a status error-code assertion. |
| `producer-azure-filter-TC001` | Publish an otherwise valid aggregate with a non-Azure billing provider. | No Contracts call, no Marketplace call, and no status message. | No component-test coverage. |
| `producer-azure-filter-TC002` | Publish an Azure aggregate with a missing or unsupported metric. | Emit `FAILED` with `UNSUPPORTED_METRIC`; do not call Contracts or Azure. | Unit coverage exists; no component-test coverage. This follows current code, which emits an error status rather than silently dropping the aggregate. |
| `producer-azure-filter-TC003` | Publish a null or keyless aggregate. | No status message and no Contracts or Azure request. | Existing `SimpleAzureComponentTest.testNullUsageMessage` also sends a valid message afterward; split/annotate it to keep one action per test. |
| `producer-azure-filter-TC004` | Publish an aggregate with a malformed timestamp. | No status message and no outbound HTTP request. | Existing `SimpleAzureComponentTest.testInvalidAzureUsageMessagesWrongDateFormat` checks the drop behavior but lacks `@TestPlanName`. |
| `producer-azure-contracts-TC001` | Return `404` from the context lookup without termination error `CONTRACTS1005`. | Emit `FAILED` with `SUBSCRIPTION_NOT_FOUND`; do not call Azure Marketplace. | Existing `SimpleAzureComponentTest.testSubscriptionNotFound`; currently lacks `@TestPlanName`. |
| `producer-azure-contracts-TC002` | Return `404` with Contracts error code `CONTRACTS1005`. | Emit `FAILED` with `SUBSCRIPTION_TERMINATED`; do not call Azure Marketplace. | No component-test coverage. |
| `producer-azure-contracts-TC003` | Return context `404` for an aggregate whose snapshot date is older than `AZURE_MARKETPLACE_USAGE_WINDOW`. | Emit `FAILED` with `INACTIVE`; do not call Azure Marketplace. Keep this separate from malformed timestamp/deserialization behavior. | No component-test coverage. |
| `producer-azure-contracts-TC004` | Return a transient Contracts error (for example `503` or timeout) until `AZURE_USAGE_CONTEXT_LOOKUP_RETRIES` is exhausted. | Verify configured retry behavior, then emit `FAILED` with `USAGE_CONTEXT_LOOKUP`; do not call Azure Marketplace. | No component-test coverage. |
| `producer-azure-marketplace-TC004` | Return each non-Accepted single-event response status: `Expired`, `Error`, `ResourceNotFound`, `ResourceNotAuthorized`, `InvalidDimension`, `InvalidQuantity`, and `BadArgument`; include HTTP `400 Bad Request`. | Pending team decision on expected status and error-code mapping. The service README describes permanent Azure rejections as failures, but current consumer code emits `SUCCEEDED` for any non-throwing response, including HTTP `200` with a non-Accepted body status; HTTP `400` is converted to `Error` and also reaches the success path. Correct the behavior contract before adding assertions. | No component-test coverage. The supplied overview explicitly left most statuses for team confirmation. |

## Phase 2 proposed HTTP API cases

These cases cover the additional inbound endpoints from `src/main/resources/META-INF/openapi.yaml`. They are not part of the team overview's Kafka-driven flow and should be included only if the team wants full inbound HTTP API coverage in this plan.

| Test plan ID | Request | Expected result |
|---|---|---|
| `producer-azure-http-TC001` | POST a valid `BillableUsage` to `/api/swatch-producer-azure/internal/azure/billable_usage` with manual submission enabled. | `202`; one corresponding message is queued on `platform.rhsm-subscriptions.billable-usage`. |
| `producer-azure-http-TC002` | POST the same request with `AZURE_MANUAL_SUBMISSION_ENABLED=false`. | `403` with the API error body/code for manual submission disabled; no billable-usage message is queued. |
| `producer-azure-http-TC003` | POST `position=beginning` to `/api/swatch-producer-azure/internal/kafka_seek_position`. | `202`; consumer seek helper is invoked for beginning. |
| `producer-azure-http-TC004` | POST `position=end` to `/api/swatch-producer-azure/internal/kafka_seek_position`. | `202`; consumer seek helper is invoked for end. |
| `producer-azure-http-TC005` | POST a valid ISO-8601 offset timestamp to `/api/swatch-producer-azure/internal/kafka_seek_timestamp`. | `202`; consumer seeks using the supplied timestamp. |
| `producer-azure-http-TC006` | POST an invalid or missing seek parameter. | Assert the agreed API error response. The OpenAPI spec does not declare these query parameters as required and does not document a specific `400` response, so confirm the error contract before implementation. |

## Existing coverage to map to plan IDs

The current `ct/` suite already exercises a subset of the Kafka flow. When it is updated, annotate each corresponding test with its exact `@TestPlanName` ID above. Unit-test coverage does not replace the component-level cases in this plan.

- `SimpleAzureComponentTest`: accepted usage, malformed timestamp, null usage message, and Contracts `404`.
- `ApiMarketplaceAzureComponentTest`: Marketplace timeout/retry exhaustion.
- `AzureBillableUsageAggregateConsumerTest` and `AzureMarketplaceServiceTest`: useful unit coverage for filtering, error handling, and credential selection; not component coverage.

## Team review decisions

1. **Scope:** Confirm whether Phase 2's three internal HTTP operations are part of the requested component-test coverage.
2. **Credential fallback:** Confirm whether context `clientId` is expected to be absent for fallback. Current code selects a matching `clientId` directly and only iterates credentials when there is no matching ID; it does not fall back after a selected client's `403`.
3. **Duplicate:** Proposed output is `SUCCEEDED` with no error code. Confirm this resolves the overview's open `Duplicate` status/error-code question.
4. **Rate limit:** The overview asks for `market_rate_limit`, while the current producer reports `UNKNOWN` after retry exhaustion. Agree the status code and update implementation/model if a dedicated rate-limit code is required.
5. **Non-Accepted Azure responses:** Decide which response-body statuses and HTTP errors must produce `FAILED`, and define their error codes. Current behavior conflicts with the README's failure description for some responses.
6. **Input filtering:** Confirm the distinctions used here: non-Azure and malformed/keyless records produce no status; unsupported metrics produce `FAILED/UNSUPPORTED_METRIC`; an old aggregate with a missing contract produces `FAILED/INACTIVE`.
7. **Contracts query:** Use the checked-in contract (`azureAccountId`, `offerId` schema) rather than stale `accountNumber` / `azureOfferId` names in the overview examples.

## Execution and framework notes

- Keep component tests under `swatch-producer-azure/ct/java/tests/` and use the existing `BaseAzureComponentTest`, Kafka bridge, and Azure WireMock facade where appropriate.
- Add `@TestPlanName` values matching the IDs in this file. Follow Given/When/Then with one action per test and verify asynchronous outcomes with the framework's `AwaitilityUtils`.
- Run locally with `./mvnw clean install -Pcomponent-tests -pl swatch-producer-azure/ct -am`; the service `ct/README.md` documents the OpenShift/Bonfire equivalent.
