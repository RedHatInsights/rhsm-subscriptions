# Introduction

The **swatch-contracts** module is a critical service within the Subscription Watch platform that serves as the central hub for managing Red Hat subscription contracts purchased through cloud marketplaces (AWS and Azure) and their underlying product offerings. It acts as the authoritative system for tracking Pay-As-You-Go (PAYG) subscription contracts, their associated metrics, and billing information. The service also handles regular non-PAYG subscriptions, as evidenced by the test cases managing all subscription types.

This comprehensive management of both contracts and offerings enables accurate capacity reporting and usage-based billing for Red Hat products sold via cloud marketplace partnerships.

This document outlines the test plan for SWATCH-3765, which involves Subscriptions in Swatch contracts.

**Purpose:** To ensure the Swatch-contracts subscription is functional, reliable, and meets all defined requirements.

**Scope**:

* Contract Lifecycle Management.
* Data Enrichment & Normalization.
* Subscription Management.
* Cloud Marketplace Context Provisioning.

**Assumptions:**

* The Swatch-contracts service is a stable and functional platform.
* The Subscription Watch service provides accurate data.

**Constraints**:

* Testing is limited to the functionality of the Swatch-contracts subscription at a component level.
* End-to-end testing in ephemeral or stage environments is out of scope for this test plan.

# Test Strategy

This test plan focuses on covering the test scenario for component-level tests, utilizing the new Java component test framework.

**Test Approach:**

* The testing will follow a **risk-based approach**, prioritizing test cases for critical functionality and high-risk areas.
* **Automated tests** will be developed for component-level checks on the new Java component test framework, while integration and end-to-end testing will be covered in another document and developed on iqe-rhsm-subscription-plugin.

**Testing Strategy:**

Test cases should be testable locally and in an ephemeral environment.

- UMB and Kafka messages can be injected for event-driven testing. 
- The services' API can be mocked. 
- System state can be verified through internal API calls.

# Test Cases

## Contract Creation via Event messages

**contracts-creation-TC001 - Process a valid PAYG contract with one valid dimension for AWS Marketplace**  
- **Description**: Verify that an AWS PAYG contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
  - Kafka topic `contracts` is available  
  - Prepare a valid AWS partner entitlement message  
- **Action:**  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Validate  
  - `billing_provider`: "aws"  
  - `billing_provider_id` formatted as `{vendorProductCode};{awsCustomerId};{sellerAccountId}`  
  - billing_account_id contains customerAwsAccountId

**contracts-creation-TC002 - Process a valid PURE PAYG contract (without dimensions) for AWS Marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:**   
  - Ensure `UMB_ENABLED=true`
  - Kafka topic `contracts` is available  
  - Prepare a valid AWS partner entitlement message  
- **Action:**     
  - Publish message to Kafka topic  
- **Verification:**   
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)

**contracts-creation-TC003 - Process a valid PAYG contract with one valid dimension for the Azure Marketplace**  
- **Description**: Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
  - Kafka topic available  
  - Prepare a valid Azure partner entitlement message  
- **Action**:  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)
  - Validate
  - `billing_provider=azure`
  - `billing_provider_id` formatted as `{azureResourceId};{planId};{vendorProductCode};{customer};{clientId}`
  - `billing_account_id` contains azureTenantId

**contracts-creation-TC004 -** **Process a valid PURE PAYG contract (without dimensions) for the Azure Marketplace**  
- **Description**: Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
  - Kafka topic available  
  - Prepare a valid Azure partner entitlement message  
- **Action**:  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response
  - Response contains status.status: "SUCCESS"
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)
  - Validate
  - `billing_provider=azure`
  - `billing_provider_id` formatted as `{azureResourceId};{planId};{vendorProductCode};{customer};{clientId}`
  - `billing_account_id` contains azureTenantId

**contracts-creation-TC005 - Process contract with multiple metrics/dimensions**  
- **Description**: Verify contracts can store multiple metrics from partner entitlement dimensions.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
  - Kafka topic available  
  - Prepare a valid partner entitlement message with multiple metrics/dimensions  
- **Action**:  
  - Publish message to Kafka topic with multiple metrics/dimensions  
- **Verification**:  
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Contract object contains multiple metrics/dimensions

**contracts-creation-TC006 - Process contract with multiple metrics/dimensions (WITH AN INVALID ONE)**  
- **Description**:  Verify contracts with multiple metrics/dimensions where one of those metrics is an invalid metric, and generate a valid contract with the valid metric.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
  - Kafka topic available  
  - Prepare a valid partner entitlement message with multiple metrics/dimensions where one of those metrics is invalid.  
- **Action**:  
  - Publish message to Kafka topic with multiple metrics/dimensions  
- **Verification**:  
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Verify the contract exists with the correct fields without the invalid metric.  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Contract object contains only valid metrics

**contracts-creation-TC007 - Process contract message with missing required fields shouldn’t persist**  
- **Description**: Verify validation errors are handled gracefully.  
- **Setup**:   
  - Ensure `UMB_ENABLED=true`
  - Kafka topic available  
  - Prepare an invalid partner entitlement message with missing required fields.  
- **Action**:  
  - Publish message to Kafka topic with missing required fields  
- **Verification**:  
  - Check error response.  
  - Query contract via internal API: GET /internal/contracts?org_id=org123  
  - Ensure the contract was not created  
- **Expected Result**:  
  - HTTP 400 Bad Request  
  - Error message indicates a missing required field

**contracts-creation-TC016 - Process a valid PAYG contract sent as an object instead of text via message broker (UMB)**
- **Description**: Verify that an AWS PAYG contract can be successfully created when receiving the message as an object instead of text.
- **Setup**:
  - Ensure `UMB_ENABLED=true`
  - Prepare a valid AWS partner entitlement message
- **Action:**
  - Publish message to UMB channel as an object instead of text
- **Verification**:
  - Query contract via internal API: GET /internal/contracts?org_id=org123
  - Verify contract exists with correct fields
  - Verify service is UP and running
- **Expected Result**:
  - HTTP 200 response
  - Response contains the created contract

## Contract Creation via Internal API

**contracts-creation-TC008** - **Create a valid PAYG contract with one valid dimension for AWS marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to `/api/swatch-contracts/internal/contracts` with a valid `ContractRequest` payload including partner_entitlement, metrics, and subscription_id.  
- **Verification:** Check response status and returned contract object.  
  - **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Validate  
  - `billing_provider`: "aws"  
  - `billing_provider_id` follows format: "{vendorProductCode};{awsCustomerId};{sellerAccountId}"  
  - billing_account_id contains customerAwsAccountId

**contracts-creation-TC009** - **Create a valid PURE PAYG contract (without dimensions) for AWS marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to `/api/swatch-contracts/internal/contracts` with a valid `ContractRequest` payload including partner_entitlement, metrics, and subscription_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)

**contracts-creation-TC010** - **Create a valid PAYG contract with one valid dimension for the Azure marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to `/api/swatch-contracts/internal/contracts` with a valid `ContractRequest` payload including partner_entitlement, metrics, and subscription_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Validate  
  - `billing_provider`: "azure"  
  - `billing_provider_id` follows Azure format  
  - billing_account_id populated correctly

**contracts-creation-TC011** - **Create a valid PURE PAYG contract (without dimensions) for the Azure marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to `/api/swatch-contracts/internal/contracts` with a valid `ContractRequest` payload including partner_entitlement, metrics, and subscription_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)

**contracts-creation-TC012** - **Create contract with multiple metrics/dimensions**  
- **Description**: Verify contracts can store multiple metrics from partner entitlement dimensions.  
- **Setup:** Prepare entitlement with multiple dimensions (e.g., cpu-hours, instance-hours).  
- **Action:** POST contract with multiple dimensions in purchase.contracts[*].dimensions.  
- **Verification:** Query created contract.  
- **Expected Result:**  
  - All metrics are stored  
  - Each metric has a correct metric_id and value

**contracts-creation-TC013** - **Create contract with multiple metrics/dimensions, but with an invalid one**  
- **Description**: Verify contracts with multiple metrics/dimensions where one of those metrics is an invalid metric, and generate a valid contract with the valid metric.  
- **Setup:** Prepare entitlement with multiple dimensions with one invalid (e.g., cpu-hours, ins-hours).  
- **Action:** POST contract with multiple dimensions in purchase.contracts[*].dimensions.  
- **Verification:** Query created contract.  
- **Expected Result:**  
  - Only valid metrics are stored  
  - Each metric stored has a correct metric_id and value

**contracts-creation-TC014** - **Create contract with missing required fields shouldn't persist**  
- **Description:** Verify validation for missing required fields.  
- **Setup:** Prepare incomplete contract request.  
- **Action:** POST contract missing partner_entitlement, or subscription_id.  
- **Verification:** Check error response.  
- **Expected Result:**  
  - HTTP 400 Bad Request  
  - Error message indicates a missing required field

**contracts-creation-TC015** - **Process contract with invalid dimensions for unconfigured SKU**  
- **Description:** Verify that contracts with invalid dimensions for SKUs without configured product tags are processed correctly by filtering invalid dimensions and logging the filtering action.  
- **Setup:**  
  - Ensure test SKU has no product tag configured in sku_product_tag table  
  - Prepare contract request with invalid dimension that doesn't match any configured metric  
- **Action:** POST contract containing invalid dimensions for unconfigured SKU  
- **Verification:**  
  - Query contract via internal API
  - Check application logs for filtering information
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract created successfully with valid dimensions only  
  - Invalid dimensions filtered out from contract  
  - Info-level log entry indicates filtered dimensions for debugging purposes  
  - System handles unconfigured SKUs with invalid dimensions gracefully without errors

## Contract Retrieval

**contracts-retrieval-TC001** - **Get contracts by org_id**  
- **Description:** Verify contracts can be retrieved by organization ID.  
- **Setup:** Create multiple contracts for org "org123" and "org456".  
- **Action:** GET `/api/swatch-contracts/internal/contracts?org_id=org123`.  
- **Verification:** Check the returned contract list.  
  - **Expected Result:**  
  - Only contracts for org123 are returned  
  - All contracts have the correct org_id

**contracts-retrieval-TC002** - **Get contracts active at a specific timestamp**  
- **Description:** Verify temporal filtering using the timestamp parameter.  
- **Setup:** Create contracts with different `start_date` and `end_date` ranges.  
- **Action:** GET contracts with a timestamp parameter set to a specific date.  
- **Verification:** Validate temporal filtering.  
- **Expected Result:**  
  - Only contracts active at the specified timestamp are returned  
  - Contracts where `start_date` <= timestamp < `end_date`

**contracts-retrieval-TC003** - **Get contracts by `billing_provider`**  
- **Description:** Verify filtering by billing provider (AWS/Azure).  
- **Setup:** Create contracts with AWS and Azure billing providers.  
- **Action:** GET contracts with `billing_provider=aws`.  
- **Verification:** Check response.  
- **Expected Result:**  
  - Only AWS contracts returned  
  - All have `billing_provider`: "aws"

**contracts-retrieval-TC004 - Get contracts with multiple parameters**  
- **Description**: Verify multiple filter parameters on the same request.  
- **Setup**: Create contracts.  
- **Action**: GET contracts with multiple parameters (timestamp, org_id, `billing_provider`).  
- **Verification**: Validate all filters.  
- **Expected** **Result**:  
  - Only contracts matching the multiple parameters should be returned.

**contracts-retrieval-TC005** - **Get contracts with no results in a specific time range**  
- **Description:** Verify behavior when no contracts match the time range criteria.  
- **Setup:** Create contracts with `start_date` and `end_date` ranges.  
- **Action:** GET contracts with a different time range from the one used on the contract creation.  
- **Verification:** Check response.  
- **Expected Result:**  
  - HTTP 200 with empty array  
  - No errors

**contracts-retrieval-TC006** - **Get contracts with no results**  
- **Description:** Verify behavior when no contracts match the criteria.  
- **Setup:** Ensure no contracts exist for org_id.  
- **Action:** GET contracts for non-existent org.  
- **Verification:** Check response.  
- **Expected Result:**  
  - HTTP 200 with empty array  
  - No errors

## Contract Update via Kafka

**contracts-update-TC001 - Process contract update message (existing contract)**  
- **Description**: Verify contract updates via messaging.  
- **Setup**:   
  - Create initial contract via message  
  - Send an updated message with the same subscription but different dates  
- **Action**:  
  - Create first message (initial contract)  
  - Wait for processing…  
  - Second message (update with different end date)  
- **Verification**: Query contract and check update timestamp and fields  
- **Expected Result**:  
  - Initial contract created  
  - Second message updates existing contract  
  - StatusResponse: "Existing contracts and subscriptions updated"  
  - End date updated to new value

**contracts-update-TC002 - Process redundant contract message**  
- **Description**: Verify idempotency - same message sent twice doesn't duplicate.  
- **Setup**: Send identical message twice  
- **Action**:* Publish the same contract message twice  
- **Verification**: Check the database for a single contract only  
- **Expected Result**:  
  - First message creates a contract  
  - Second message: StatusResponse "Redundant message ignored"  
  - No duplicate contracts created

## Contract Update via API

**contracts-update-TC003 - Update contract start and end date**  
- **Description**: Verify updating the contract start and end date.  
- **Setup:**  
  - Create a contract with:   
  - `subscription_number`: "12585274"  
  - `start_date`: "2024-01-01T00:00:00Z"  
  - `end_date`: "2024-12-31T23:59:59Z"  
  - Cores: 8  
- **Action:** Update the contract start and end date.  
- **Verification:**   
  - Query contract by `subscription_number` and verify changes  
  - Check `last_updated` timestamp  
  - Check the start date  
  - Check the end date  
- **Expected Result:**  
  - Existing contract located by `subscription_number` + `start_date` match  
  - `start_date` update  
  - `end_date` updated   
  - `last_updated` timestamp updated to the current time  
  - UUID remains the same (no new contract created)  
  - Other fields unchanged (org_id, sku, metrics)  
  - Response status: "EXISTING_CONTRACTS_SYNCED"

**contracts-update-TC004 - Update contract end date (renewal)**  
- **Description**: Verify updating the contract end date ( with a date in the future ) when the customer renews the subscription.  
- **Setup:**  
  - Create a contract with:   
  - `subscription_number`: "12585274"  
  - `start_date`: "2024-01-01T00:00:00Z"  
  - `end_date`:  "2024-12-31T23:59:59Z"  
  - Cores: 8  
- **Action:** Update the contract end-date with a date in the future ("2025-12-31T23:59:59Z") 
- **Verification:**   
  - Query contract by `subscription_number` and verify changes  
  - Check `last_updated` timestamp  
  - Check end-date  
- **Expected Result:**  
  - Existing contract located by `subscription_number` + `start_date` match  
  - `end_date` updated from "2024-12-31T23:59:59Z" to "2025-12-31T23:59:59Z"  
  - `last_updated` timestamp updated to the current time  
  - UUID remains the same (no new contract created)  
  - Other fields unchanged (org_id, sku, metrics)  
  - Response status: "EXISTING_CONTRACTS_SYNCED"

**contracts-update-TC005 - Update contract metrics for Payg**   
- **Description**: Verify updating contract metrics when the customer upgrades the tier.  
- **Setup**:  
  - Create a contract with Cores: 8, Instance-hours: 100  
- **Action**: Submit updated entitlement with Cores: 16, Instance-hours: 200  
- **Expected Result**:  
  - Existing contract found and updated  
  - Cores metric updated: 8 → 16  
  - Instance-hours metric updated: 100 → 200  
  - `last_updated` timestamp changed  
  - contract.status.message == "Existing contracts and subscriptions updated"  
  - contract.status.status == "SUCCESS"

**contracts-update-TC006 - Update contract metrics from pure payg(no metrics before) to payg(with metrics)**  
- **Description**: Verify updating contract metrics when the customer upgrades the tier.  
- **Setup**:  
  - Create a contract with Cores: 8, Instance-hours: 100  
- **Action**: Submit updated entitlement with Cores: 16, Instance-hours: 200  
- **Expected Result**:  
  - Existing contract found and updated  
  - Cores metric updated: 8 → 16  
  - Instance-hours metric updated: 100 → 200  
  - `last_updated` timestamp changed  
  - contract.status.message == "Existing contracts and subscriptions updated"  
  - contract.status.status == "SUCCESS"

**contracts-update-TC007 - Update contract - terminate (set end date)**  
- **Description**: Verify terminating contract by setting end date to current time.  
- **Setup**:  
  - Create a contract with an end date in the future date  
- **Action**: Submit entitlement with `end_date`: current timestamp  
- **Expected** **Result**:  
  - `end_date` set to termination timestamp  
  - Contract becomes inactive  
  - Subscription `end_date` also updated

**contracts-update-TC008 - Update contract - add new metric**  
- **Description:** Verify adding a new metric to an existing contract.  
- **Setup**:  
  - Create a contract with one metric.  
- **Action**: Update the contract with another metric  
- **Expected** **Result**:  
  - Existing contract found and updated  
  - Old metric remains  
  - New metric added  
  - `last_updated` timestamp changed  
  - contract.status.message == "Existing contracts and subscriptions updated"  
  - contract.status.status == "SUCCESS"

**contracts-update-TC009 - Update contract - remove metric**  
- **Description**: Verify that a metric is removed from an existing contract.  
- **Setup**:  
  - Create a contract with multiple metrics.  
- **Action**: Remove one metric from the contract.  
- **Expected** **Result**:  
  - Existing contract found and updated  
  - The specified metric was removed from the contract  
  - The metric that was not removed from the contract remains unchanged  
  - `last_updated` timestamp changed  
  - contract.status.message == "Existing contracts and subscriptions updated"  
  - contract.status.status == "SUCCESS"

## Contract Termination

**contracts-termination-TC001 - A contract remains active after receiving a non-termination event.**
- **Description:** Verify that a UMB message with a non-terminating status (i.e., `"status": "SUBSCRIBED"`) does not cause a contract to be terminated.
- **Setup:** Ensure a contract exists and is currently in an active state.
- **Action:** Simulate a UMB message from the IT partner gateway for the active contract, with the field "status": "SUBSCRIBED".
- **Verification:** Check the contract using the GET API.
- **Expected Result:**
  - The end date is empty or higher than now.

**contracts-update-TC002 - Terminate an existing and active contract.**
- **Description:*** Verify that an active contract can be successfully terminated when a UMB message with a status: "UNSUBSCRIBED" is received.
- **Setup:** Ensure a contract exists and is currently in an active state.
- **Action:** Simulate a UMB message from the IT partner gateway for the active contract, with the field `"status": "UNSUBSCRIBED"`.
- **Verification:** Check the contract using the GET API.
- **Expected Result:**
  - The end date should be the one contained in the UMB message.

**contracts-termination-TC003 - Receive a termination event for an already terminated contract.**
- **Description:** Verify that receiving a UMB message with `"status": "UNSUBSCRIBED"` for a contract that is already in a terminated state does not cause an error or change its status.
- **Setup:** Ensure a contract exists and its status is already 'TERMINATED'.
- **Action:** Simulate a UMB message from the IT partner gateway for the terminated contract, with the field `"status": "UNSUBSCRIBED"`.
- **Verification:** Check the contract using the GET API.
- **Expected Result:**
  - No errors should be logged.
  - The contract's status should remain “TERMINATED”.
  - The end date is updated.

**contracts-termination-TC004 - Receive a termination event for a non-existing contract.**
- **Description:** Verify that a contract with a subscription_number but no corresponding record in the Subscriptions table can be correctly terminated when a UMB message with a `"status": "UNSUBSCRIBED"` is received.
- **Setup:** Ensure a contract does not exist in the Contract table.
- **Action:** Simulate a UMB message from the IT partner gateway for the contract unexisting contract, and with the field `"status": "UNSUBSCRIBED"`.
- **Verification:** Check the contract using the GET API.
- **Expected result:**
  - The contract is created.
  - Its end date is empty or higher than now.

**contracts-termination-TC005 - Receive a subscribed event for a terminated contract.**
- **Description:** Verify that a terminated contract can be re-enabled when a UMB message with `"status": "SUBSCRIBED"` is received for the same org, SKU, subscription, and billing account ID with a new date.
- **Setup:** Ensure a contract exists and is currently in a terminated state (has an end date).
- **Action:** Simulate a UMB message from the IT partner gateway for the terminated contract, with the field `"status": "SUBSCRIBED"` and a new date.
- **Verification:** Check the contract using the GET API.
- **Expected Result:**
  1. The contract is updated and returns to active status.
  2. The contract's end date is cleared or set to a future date.
  3. The contract status reflects the re-subscription.

**contracts-termination-TC006 - Ensure capacity has decreased after a contract is terminated.**
- **Description:** Verify that the capacity of the contract is decreased after a contract is terminated.
- **Setup:** Ensure a contract exists and is currently in an active state.
- **Action:** Simulate a message to the subscription service to terminate an active contract.
- **Verification:** Check the capacity of the contract using the GET API.
- **Expected Result:**
  1. The capacity of the contract is decreased.
  
**contracts-termination-TC007 - Ensure the subscription table is updated after a contract is terminated.**
- **Description:** Verify that the subscription table is updated after a contract is terminated.
- **Setup:** Ensure a contract exists and is currently in an active state.
- **Action:** Simulate a message to the subscription service to terminate an active contract.
- **Verification:** Check the subscription table using the GET API.
- **Expected Result:**
  1. The subscription table is updated with the correct next_event_date of tomorrow and 23:59:59.
  2. The subscription table is updated with the correct next_event_type of 'Subscription End'

## Contract Deletion

**contracts-deletion-TC001** - **Delete contract by UUID**  
- **Description:** Verify hard deletion of contract by UUID.  
- **Setup:** Create a contract and note its UUID.  
- **Action:** DELETE `/api/swatch-contracts/internal/contracts/{uuid}`.  
- **Verification:** 
  - Verify contract is no longer retrievable
- **Expected Result:**  
  - HTTP 204 No Content response
  - Contract no longer returned by organization contract lookup

**contracts-deletion-TC002** - **Delete non-existent contract**  
- **Description:** Verify graceful handling for deleting a non-existent UUID.  
- **Setup:** Generate a random UUID that doesn't exist.  
- **Action:** DELETE contract with invalid UUID.  
- **Verification:** Check response for idempotent behavior.  
- **Expected Result:**  
  - HTTP 204 No Content (idempotent behavior - delete succeeds regardless)  
  - Graceful handling of non-existent contracts

## Contract Sync

**contracts-sync-TC001 - Sync contracts for a single organization**  
- **Description**: Verify contract sync triggers for a specific org.  
- **Setup**: Have upstream contracts available for the org.  
- **Action**: POST `/api/swatch-contracts/internal/rpc/sync/contracts/{org_id}`.  
- **Verification**: Check contracts are synced.  
- **Expected Result**:  
  - HTTP 200 with StatusResponse  
  - Contracts from upstream are created/updated  
  - Status: "Success"

**contracts-sync-TC002 - Sync with delete contracts and subscriptions**  
- **Description**: Verify the `delete_contracts_and_subs` parameter.  
- **Setup**: Create contracts and subscriptions for org.  
- **Action**: POST sync with `delete_contracts_and_subs=true`.  
- **Verification**: Check database state.  
  - **Expected Result**:  
  - All contracts deleted before sync  
  - All PAYG subscriptions deleted before sync  
  - Fresh sync performed

**contracts-sync-TC003 - Sync all contracts across all organizations**  
- **Description**: Verify syncAllContracts triggers sync for all orgs with contracts.  
- **Setup**: Have multiple orgs with contracts.  
- **Action**: POST `/internal/rpc/syncAllContracts`.  
- **Verification**: Monitor sync progress.  
- **Expected Result**:  
  - HTTP 200 OK
  - StatusResponse: "All Contracts are Synced"
  - Each org's contracts synced

**contracts-sync-TC004** - Sync subscriptions for contracts by org**  
- **Description**: Verify subscription sync for all contracts of an org.  
- **Setup**: Have contracts for org without subscriptions.  
- **Action**: POST `/api/swatch-contracts/internal/rpc/sync/contracts/{org_id}/subscriptions`.  
- **Verification**: Check subscriptions are created.  
  - **Expected Result**:  
  - StatusResponse success  
  - Subscriptions synced from Subscription API

**contracts-sync-TC005 - Clear all contracts for the organization**
- **Description**: Verify that deleteContractsByOrg removes all org contracts.
- **Setup**: Create multiple contracts for the org.
- **Action**: DELETE `/api/swatch-contracts/internal/rpc/reset/{org_id}`.
- **Verification**: Query contracts for org.
- **Expected Result**:
  - HTTP 204 No Content
  - No contracts remain for org_id

## Subscription Management via UMB

**subscriptions-creation-TC001 - Process a valid UMB subscription XML message from UMB**  
- **Description**: Verify subscription creation via UMB XML message.  
- **Setup**:  
  - Ensure `UMB_ENABLED=true`
- **Action**:  
  - Publish message to `VirtualTopic.canonical.subscription` channel
- **Verification**:  
  - Query subscription via internal API  
  - Verify subscription created  
- **Expected Result**:  
- XML parsed successfully via `CanonicalMessage.createMapper()`  
- Subscription entity created for org  
- `subscription_number`  
- quantity  
- sku
- Start and end dates are correctly parsed  

**subscriptions-creation-TC002 - Process UMB subscription with AWS external references**  
- **Description**: Verify AWS marketplace subscription data extraction from UMB.  
- **Action**:  
  - Publish message to `VirtualTopic.canonical.subscription` channel  
- **Verification**: Query subscription and check AWS fields  
- **Expected Result**:  
  - Subscription created with AWS external references  
  - `billing_provider`  
  - `billing_provider_id` contains AWS identifiers  
  - `billing_account_id`

**subscriptions-creation-TC003 - Process UMB subscription with Azure external references**  
- **Description**: Verify Azure marketplace subscription data from UMB.  
- **Action**:  
  - Publish message to `SUBSCRIPTION_SYNC_TASK_UMB` Kafka topic  
- **Verification**: Check Azure-specific fields  
- **Expected Result**:  
- Subscription created with null references since subscription sync does not populate the Azure external references

**subscriptions-creation-TC004 - Process malformed UMB XML message**  
- **Description**: Verify error handling for invalid XML.  
- **Action**:  
  - Publish malformed UMB XML message to `VirtualTopic.canonical.subscription` channel  
- **Verification**: Subscription not created  
- **Expected Result**:  
  - `JsonProcessingException` thrown (XML parsing error)  
  - No subscription created  
  - Message handling fails gracefully

**subscriptions-creation-TC005 - Process UMB message with missing required fields**  
- **Description**: Verify validation for incomplete subscription data.  
- **Action**:  
  - Publish the UMB message with missing required fields to `VirtualTopic.canonical.subscription` channel  
- **Verification**: Check for validation errors  
- **Expected Result**:  
  - Validation failure or graceful error handling  
  - No subscription created with incomplete data  
  - Error logged with details

**subscriptions-creation-TC006 - Process subscription update via UMB**  
- **Description**: Verify subscription updates through messaging.  
- **Setup**:  
  - Send initial subscription message  
  - Send an update with a different quantity or dates  
- **Action**: Publish initial message, then update the message  
- **Verification**: Check that the subscription record is updated  
- **Expected Result**:  
  - Initial subscription created  
  - Update message modifies existing subscription  
  - Updated fields reflected in the database  
  - No duplicate subscriptions

**subscriptions-creation-TC007 - Process terminated subscription via UMB**  
- **Description**: Verify subscription termination messages.  
- **Action**:  
  - Publish message to `VirtualTopic.canonical.subscription` channel  
  - Update the end date to the current timestamp  
- **Verification**: Check subscription `end_date` updated  
- **Expected Result**:  
  - Subscription marked as terminated  
  - `end_date` set to termination date  
  - Status reflects termination

## Subscription Management via API

**subscriptions-creation-TC009** - **Create a valid PAYG contract and verify the Contract/Subscription table**  
- **Description:** Verify the contract/subscription after a contract/subscription creation.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to `/api/swatch-contracts/internal/contracts`   
- **Verification:** Check response status and returned contract/subscription object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, `subscription_number`, sku, `start_date`, `end_date`, org_id, `billing_provider`, etc.)  
  - Validate  
  - Contract/subscription table.

**subscriptions-creation-TC010** - **Save subscriptions PAYG**  
- **Description:** Verify subscription saving when enabled.  
- **Setup:** Prepare subscriptions with JSON array  
- **Action:**   
  - POST `/api/swatch-contracts/internal/subscriptions` with JSON array.  
  - Sync subscriptions  
- **Verification:** Query saved subscriptions.  
  - Expected Result:  
  - SubscriptionResponse: "Success"  
  - Subscriptions persisted  
  - Multiple subscriptions created from an array  
- **Note:** This endpoint **SUPPORTS multiple subscriptions** via JSON array

**subscriptions-sync-TC001 - Sync all subscriptions for enabled orgs**  
- **Description**: Verify PUT /rpc/subscriptions/sync enqueues org subscriptions.  
- **Setup**: Configure sync-enabled orgs.  
- **Action:** PUT `/api/swatch-contracts/internal/rpc/subscriptions/sync`.  
- **Verification**: Monitor sync queue.  
- **Expected Result:**  
  - RpcResponse with success

**subscriptions-sync-TC002** - **Sync UMB subscription XML message**  
- **Description:** Verify processing of UMB CanonicalMessage XML.  
- **Setup:** Prepare a valid UMB subscription XML.  
 **Action:**   
  - POST `/api/swatch-contracts/internal/subscriptions/umb` with XML.  
  - Sync subscriptions  
- **Verification:** Check subscription created.  
- **Expected Result:**  
  - XML parsed correctly  
  - Subscription entity created  
  - SubscriptionResponse: "Success"

**subscriptions-termination-TC001** - **Terminate subscription with timestamp**  
- **Description:** Verify manual subscription termination.  
- **Setup:** Create an active subscription.  
- **Action:** POST `/api/swatch-contracts/internal/subscriptions/terminate/{subscription_id}?timestamp=2024-01-01T00:00:00Z`.  
- **Verification:** Check subscription end date.  
- **Expected Result:**  
  - TerminationRequest with message  
  - Subscription `end_date` set to timestamp  
  - Subscription effectively terminated

## Offering Synchronization

**offering-sync-TC001** - **Synchronize offering from external product data**
- **Description:** Verify that offerings can be synchronized using external product information and result in correct database state.
- **Setup:** Create test product data with specific attributes (level_1, level_2, metered flag) in the external product service.
- **Action:** Send UMB message to trigger offering synchronization from external product data.
- **Verification:** Use internal GET API to verify offering synchronization and product tag mapping.
- **Expected Result:**
  - API returns HTTP 200 response with correct product tag.
  - Product tag matches expected value based on level_1/level_2 attributes.
  - Offering synchronization completes without errors.

**offering-sync-TC002: Handle synchronization of non-existent offering**
- **Description:** Verify that attempting to synchronize an invalid or non-existent SKU is handled appropriately.
- **Setup:** Ensure no product data exists for test SKU "INVALID_SKU" in external product service.
- **Action:** Send UMB message for non-existent SKU to test error handling.
- **Verification:** Check that no offering data is created.
- **Expected Result:**
  - No offering record created for invalid SKU.
  - No database corruption occurs.
  - The system handles invalid SKU gracefully.

**offering-sync-TC003: Synchronize metered offering**
- **Description:** Verify that metered offerings are synchronized correctly with proper metered flag.
- **Setup:** Create test product data with metered="y" attribute in external product service.
- **Action:** Send UMB message to trigger offering synchronization.
- **Verification:** Use internal GET API to verify offering synchronization succeeded for metered SKU.
- **Expected Result:**
  - API returns HTTP 200 response indicating successful synchronization.
  - Product tag returned matches expected value for metered offering.
  - Offering synchronization completes without errors.

**offering-sync-TC004: Synchronize unlimited capacity offering**
- **Description:** Verify that unlimited capacity offerings are synchronized correctly with proper unlimited flag.
- **Setup:** Create test product data with has_unlimited_usage=True attribute in external product service.
- **Action:** Send UMB message to trigger offering synchronization.
- **Verification:** Use internal GET API to verify offering synchronization succeeded for unlimited capacity SKU.
- **Expected Result:**
  - API returns HTTP 200 response indicating successful synchronization.
  - Product tag returned matches expected value for unlimited offering.
  - Offering synchronization completes without errors.

## Product Tag Management

**offering-tags-TC001: Retrieve product tags for synchronized offering**
- **Description:** Verify that product tags can be retrieved correctly for offerings that have been synchronized.
- **Setup:** Create test product with specific level_1 and level_2 values (`product_id`, sku, level_1, level_2) in external product service.
- **Action:** Query public API endpoint to retrieve product tags for the SKU.
- **Verification:** Use internal GET API and verify the response contains the expected product tag.
- **Expected Result:**
  - API returns HTTP 200 response with correct product tag.
  - Product tag matches expected value based on level_1 and level_2 values.
  - Subsequent API calls return consistent tag data.

**offering-tags-TC002: Verify product tag mapping for different product types**
- **Description:** Verify that different level_1/level_2 combinations result in correct product tag assignments.
- **Setup:** Create test products with various level_1/level_2 combinations to test different product structures.
- **Action:** Query public API endpoint to retrieve product tags for each product type.
- **Verification:** Verify each product returns appropriate product tags based on level_1/level_2 values.
- **Expected Result:**
  - Product tags correctly generated from level_1/level_2 combinations.
  - API responses for different SKUs are consistent and accurate.
  - Different level combinations produce distinct product tags.

**offering-tags-TC003: Handle product tag retrieval for non-existent offering**
- **Description:** Verify that retrieving product tags for non-existent offerings is handled appropriately.
- **Setup:** Ensure no offering exists for test SKU "`NONEXISTENT_SKU`".
- **Action:** Query public API endpoint for product tags of non-existent SKU.
- **Verification:** Check that the operation handles missing offering gracefully.
- **Expected Result:**
  - Operation completes without causing system errors.
  - API response properly handles non-existent SKU (appropriate error code or message).

## Capacity Management

**offering-capacity-TC001: Verify capacity calculation for metered offerings**
- **Description:** Verify that capacity calculations for metered offerings accurately reflect subscription quantity and offering attributes.
- **Setup:** Create test metered offering and subscription with a set quantity.
- **Action:** Query public capacity report API endpoint for the SKU.
- **Verification:** Verify API response contains correct capacity values based on subscription quantity and offering metrics.
- **Expected Result:**
  - Capacity calculation reflects subscription quantity multiplied by offering metrics.
  - Multiple metric dimensions (Sockets, Cores) calculated correctly.
  - API response contains subscription reference and capacity details.

**offering-capacity-TC002: Verify unlimited capacity offering handling**
- **Description:** Verify that unlimited capacity SKUs display correct unlimited status in capacity reports.
- **Setup:** Create test unlimited offering (metered="n", `has_unlimited_usage`=True) and subscription.
- **Action:** Query public capacity report API endpoint for the SKU.
- **Verification:** Verify API response indicates unlimited capacity status.
- **Expected Result:**
  - API response shows unlimited capacity flag set.
  - Capacity values indicate unlimited status appropriately.
  - Subscription correctly linked to unlimited offering in response.

## Offering Update

**offering-update-TC001: Process product update event**
- **Description:** Verify that UMB update events correctly modify existing offering attributes without data loss.
- **Setup:** Create existing offering through external product service, then prepare UMB update message with different attributes.
- **Action:** Send UMB product update event through message broker.
- **Verification:** Use internal GET API to verify offering updates were applied correctly.
- **Expected Result:**
  - API returns HTTP 200 response with updated product tag.
  - Product tag reflects changes from updated attributes.
  - Update operation completes without errors.

**offering-update-TC002: Handle malformed event**
- **Description:** Verify that the malformed UMB message is handled gracefully without affecting system stability.
- **Setup:** Prepare one malformed UMB message (invalid JSON).
- **Action:** Send the malformed UMB message through message broker.
- **Verification:** Check system logs and verify no offering data corruption, system remains operational.
- **Expected Result:**
  - System processes the malformed event without crashing or data corruption.
  - Valid offerings remain unaffected by malformed UMB events.
  - Appropriate error handling and logging for debugging malformed events.

## Contract Integration

**offering-contract-TC001: Create contract with offering capacity**

- **Description:** Verify that contracts correctly reference and allocate offering capacity during creation.
- **Setup:** Create test offering and subscription.
- **Action:** Create a contract using public contract API with offering-based capacity.
- **Verification:** Query contract API to verify contract creation and capacity allocation matches offering attributes.
- **Expected Result:**
  - Contract created successfully with capacity reflecting offering definitions.
  - Contract dimensions reference offering capacity metrics.
  - Contract capacity calculations integrate offering attributes correctly.

**offering-contract-TC002: Verify contract capacity with different offering types**

- **Description:** Verify that contracts with different offering types (metered vs unlimited) handle capacity correctly.
- **Setup:** Create test offerings with different capacity types (metered="y", `has_unlimited_usage`=True).
- **Action:** Create contracts for each offering type using public contract API.
- **Verification:** Query contract API and compare capacity allocation for metered vs unlimited offerings.
- **Expected Result:**
  - Metered offerings result in quantified contract capacity.
  - Unlimited offerings show appropriate unlimited capacity flags.
  - Contract creation adapts correctly to different offering capacity types.

## Marketplace Integration

**offering-marketplace-TC001: Create marketplace contract with offering dimensions**
- **Description:** Verify that marketplace contracts correctly integrate offering capacity with billing provider specific dimensions.
- **Setup:** Create test marketplace product, configure billing provider dimensions.
- **Action:** Create marketplace contract using public API with billing_provider (aws/azure) and offering-based capacity.
- **Verification:** Query contract API to verify marketplace dimensions are correctly mapped to offering attributes.
- **Expected Result:**
  - Marketplace contract created with billing provider specific capacity metrics.
  - Offering dimensions correctly mapped to marketplace contract structure.
  - Contract capacity reflects both offering attributes and marketplace billing model.

**offering-marketplace-TC002: Handle offering capacity with different billing providers**
- **Description:** Verify that the same offering can be used with different marketplace billing providers correctly.
- **Setup:** Create test offering, prepare for multiple billing provider contracts.
- **Action:** Create contracts using public API with billing_provider="aws" and billing_provider="azure" for the same offering.
- **Verification:** Query contract API to verify both contracts handle offering capacity correctly for their respective billing models.
- **Expected Result:**
  - Same offering supports multiple marketplace billing providers.
  - AWS and Azure contracts correctly adapt offering capacity to their billing models.
  - Offering attributes preserved across different marketplace implementations.

## Basic Capacity Report Generation

*capacity-report-TC001 - Get V2 SKU capacity report**
- **Description:** Verify the V2 endpoint with an enhanced measurement array.
- **Setup:** Create subscriptions with multiple metrics for a product that supports multiple dimensions.
- **Action:** GET /api/rhsm-subscriptions/v2/subscriptions/products/{product_id}
- **Verification:** Ensure the subscription was successfully created.
- **Expected Result:**
  - SkuCapacityReport_V2 returned
  - The measurements field is an array of doubles, matching the received values (e.g., [8.0, 2.0])
  - Meta includes measurements array with metric names corresponding to product metrics

**capacity-report-TC002 -  Get Capacity Report by Product and Metric**
- **Description**: Verify capacity report retrieval for a specific product and metric
- **Setup**:
  - Subscriptions exist with capacity data
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create subscriptions for a product with a specific metric
  2. GET capacity report for the product and metric
  3. Specify granularity=DAILY, time range (beginning, ending)
- **Expected Results**:
  - HTTP 200 OK
  - Response contains a data array with CapacitySnapshotByMetricId objects
  - Each snapshot has: date, value, hasData
  - Meta object includes: product, metricId, granularity, count
  - Capacity values match subscription measurements

**capacity-report-TC003 - Capacity Report with Multiple Subscriptions**
- **Description**: Verify capacity aggregation across multiple subscriptions
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create 3 subscriptions for the same product with varying capacity values
  2. All subscriptions active in the queried time range
  3. GET capacity report
- **Expected Results**:
  - Capacity value = sum of all subscriptions
  - Single data point per time period

**capacity-report-TC004 - Capacity Report with No Data**
- **Description**: Verify behavior when no subscriptions match the criteria
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. GET capacity report for a product with no subscriptions
- **Expected Results**:
  - HTTP 200 OK
  - data array has entries with value=0 and hasData=false
  - One entry per time period

**capacity-report-TC005 - Capacity Report with Expired Subscriptions**
- **Description**: Verify only active subscriptions are included
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with an end_date in the past
  2. Create an active subscription for the same product
  3. GET capacity report
- **Expected Results**:
  - Only active subscription capacity included
  - Expired subscription excluded

## Capacity Granularity Testing
**capacity-report-granularity-TC001 - Hourly Granularity Report**
- **Description**: Verify capacity report with hourly granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=HOURLY for 24-hour range
- **Expected Results**:
  - 24 data points returned (one per hour)
  - Each snapshot is aligned to the hour boundary
  - Consistent capacity values across hours

**capacity-report-granularity-TC002 - Daily Granularity Report**
- **Description**: Verify the daily granularity capacity report
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=DAILY for 7-day range
- **Expected Results**:
  - 7 data points (one per day)
  - Timestamps aligned to the day start

**capacity-report-granularity-TC003 - Weekly Granularity Report**
- **Description**: Verify weekly granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=WEEKLY for a 4-week range
- **Expected Results**:
  - 4 data points
  - Weekly boundaries respected

**capacity-report-granularity-TC004 - Monthly Granularity Report**
- **Description**: Verify monthly granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=MONTHLY for a 6-month range
- **Expected Results**:
  - 6 data points
  - Month boundaries are correctly handled

**capacity-report-granularity-TC005 - Quarterly Granularity Report**
- **Description**: Verify quarterly granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=QUARTERLY for a 1-year range
- **Expected Results**:
  - 4 data points
  - Quarter boundaries (Jan 1, Apr 1, Jul 1, Oct 1)

**capacity-report-granularity-TC006 - Yearly Granularity Report**
- **Description**: Verify yearly granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity with granularity=YEARLY for a 3-year range
- **Expected Results**:
  - 3 data points
  - Year boundaries aligned

**capacity-report-granularity-TC007 - Invalid Granularity for Product**
- **Description**: Verify error when requesting unsupported granularity
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with capacity
  2. GET capacity for unsupported granularity with granularity=HOURLLY
- **Expected Results**:
  - HTTP 400 Bad Request

## Capacity Filtering
**capacity-report-filtering-TC001 - Filter by Service Level**
- **Description**: Verify capacity filtering by service level
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?sla={service_level}`
- **Test Steps**:
  1. Create subscriptions with different SLAs (Premium, Standard)
  2. GET capacity with sla=Premium
- **Expected Results**:
  - Only Premium subscription capacity included
  - Standard subscriptions excluded

**capacity-report-filtering-TC002 - Filter by Usage**
- **Description**: Verify capacity filtering by usage type
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?usage={usage}`
- **Test Steps**:
  1. Create subscriptions with Production and Development usage
  2. GET capacity with usage=Production
- **Expected Results**:
  - Only Production subscription capacity included

**capacity-report-filtering-TC003 - Filter by Billing Account**
- **Description**: Verify capacity filtering by billing account ID
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?billing_account_id={account_id}`
- **Test Steps**:
  1. Create subscriptions for different billing accounts (AWS/Azure account IDs)
  2. GET capacity with billing_account_id filter
- **Expected Results**:
  - Only specified billing account capacity returned

**capacity-report-filtering-TC004 - Filter by Report Category (Physical)**
- **Description**: Verify filtering for PHYSICAL capacity only
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?category=NON_HYPERVISOR`
- **Test Steps**:
  1. Create a subscription with PHYSICAL Cores and HYPERVISOR Cores
  2. GET capacity with category=NON_HYPERVISOR
- **Expected Results**:
  - Only PHYSICAL measurements are included in the capacity
  - HYPERVISOR excluded

**capacity-report-filtering-TC005 - Filter by Report Category (Hypervisor)**
- **Description**: Verify filtering for HYPERVISOR capacity only
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?category=HYPERVISOR`
- **Test Steps**:
  1. Create a subscription with both measurement types
  2. GET capacity with category=HYPERVISOR
- **Expected Results**:
  - Only HYPERVISOR measurements included
  - PHYSICAL excluded

**capacity-report-filtering-TC006 - Combined Filters**
- **Description**: Verify multiple filters work together
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?sla={sla}&usage={usage}&category={category}`
- **Test Steps**:
  1. Create a diverse subscription set
  2. GET capacity with multiple filters (sla=Premium, usage=Production, category=PHYSICAL)
- **Expected Results**:
  - Only subscriptions matching ALL criteria included
  - Correct AND logic applied

## Capacity Pagination
**capacity-report-pagination-TC001- Paginated Capacity Report**
- **Description**: Verify pagination of capacity data
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?offset={offset}&limit={limit}`
- **Test Steps**:
  1. Generate capacity data with 100 data points
  2. GET capacity with offset=0, limit=10
- **Expected Results**:
  - Response contains 10 data points
  - Links object populated with first, last, next, previous
  - Meta.count = 100

**capacity-report-pagination-TC002 - Navigate Pagination Links**
- **Description**: Verify pagination navigation
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?offset={offset}&limit={limit}`
- **Test Steps**:
  1. GET first page (offset=0, limit=10)
  2. Follow the "next" link
  3. Verify the second page
- **Expected Results**:
  - Second page has offset=10
  - Different data points returned
  - The previous link points to the first page

**capacity-report-pagination-TC003 - Last Page Pagination**
- **Description**: Verify the last page has no "next" link
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}?offset={offset}&limit={limit}`
- **Test Steps**:
  1. GET the last page of results
- **Expected Results**:
  - Next link is null
  - Previous link populated

## Unlimited Capacity
**capacity-report-unlimited-quantity-TC001 - Subscription with Unlimited Usage**
- **Description**: Verify the has_infinite_quantity flag for unlimited subscriptions
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with has_unlimited_usage=true
  2. GET capacity report
- **Expected Results**:
  - CapacitySnapshot.hasInfiniteQuantity = true
  - Capacity value still calculated from measurements

**capacity-report-unlimited-quantity-TC002 - Mixed Limited and Unlimited Subscriptions**
- **Description**: Verify the unlimited flag when both types exist
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create an unlimited subscription active in the time range
  2. Create regular subscriptions
  3. GET capacity
- **Expected Results**:
  - hasInfiniteQuantity = true for snapshots where unlimited subscription is active
  - hasInfiniteQuantity = false for time periods outside the unlimited subscription range

## Temporal Boundaries

This section validates capacity calculations across time boundaries. Tests verify correct handling of subscriptions that start or end during report periods, subscriptions completely outside the time range, and proper aggregation when multiple subscriptions have overlapping date ranges.

**capacity-report-temporal-boundaries-TC001 - Subscription Starts During Report Period**
- **Description**: Verify capacity when subscription starts mid-period
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription starting on day 5 of the 10-day report period
  2. GET daily capacity report
- **Expected Results**:
  - Days 1-4: value=0, hasData=false (or absent subscription)
  - Days 5-10: value=subscription capacity, hasData=true

**capacity-report-temporal-boundaries-TC002 - Subscription Ends During Report Period**
- **Description**: Verify capacity when subscription ends mid-period
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription ending on day 5 of 10 days
  2. GET capacity
- **Expected Results**:
  - Days 1-4: capacity included
  - Days 5-10: capacity excluded

**capacity-report-temporal-boundaries-TC003 - Subscription Completely Outside Range**
- **Description**: Verify subscription outside the time range is excluded
- **Setup**:
  - User authenticated with a valid org_id
- **Action**: `GET /api/rhsm-subscriptions/v1/capacity/products/{product_id}/{metric_id}`
- **Test Steps**:
  1. Create a subscription with dates outside the report range
  2. GET capacity
- **Expected Results**:
  - Subscription not included in any snapshots
  - All snapshots have value=0 or a minimal value from other subscriptions

## Capacity Reconciliation
### Offering-Level Reconciliation
**capacity-reconciliation-TC001 - Reconcile Capacity for Single Offering**
- **Description**: Verify reconciliation triggers for all subscriptions of an offering
- **Action**: `CapacityReconciliationService.reconcileCapacityForOffering(String sku)`
- **Test Steps**:
  1. Create an offering.
  2. Create 5 subscriptions for this offering
  3. Call capacityReconciliationService.reconcileCapacityForOffering(SKU_NUMBER)
- **Expected Results**:
  - ReconcileCapacityByOfferingTask messages published to Kafka
  - All subscriptions processed
  - Subscription measurements updated

**capacity-reconciliation-TC002 - Reconcile Non-existent Offering**
- **Description**: Verify handling when the offering doesn't exist
- **Action**: `CapacityReconciliationService.reconcileCapacityForOffering(String sku)`
- **Test Steps**:
  1. Call reconcileCapacityForOffering with an invalid SKU
- **Expected Results**:
  - No errors thrown
  - No tasks published (subscription count = 0)

**capacity-reconciliation-TC003 - Force Reconcile via API**
- **Description**: Verify force reconciliation endpoint
- **Action**: `POST /api/swatch-contracts/internal/rpc/forceReconcileOffering`
- **Test Steps**:
  1. POST request with SKU
  2. Verify reconciliation triggered
- **Expected Results**:
  - HTTP 200 OK
  - Reconciliation task enqueued

### Subscription-Level Reconciliation
**capacity-reconciliation-TC004 - Reconcile Subscription with Cores**
- **Description**: Verify capacity calculation for subscription with Cores offering
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Create offering with cores=4, hypervisorCores=2
  2. Create a subscription with quantity=10
  3. Reconcile subscription
- **Expected Results**:
  - PHYSICAL Cores measurement = 4 * 10 = 40
  - HYPERVISOR Cores measurement = 2 * 10 = 20
  - Measurements persisted in subscription_measurements

**capacity-reconciliation-TC005 - Reconcile Subscription with Sockets**
- **Description**: Verify capacity calculation for Sockets
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Create offering with sockets=2, hypervisorSockets=1
  2. Create a subscription with quantity=5
  3. Reconcile
- **Expected Results**:
  - PHYSICAL Sockets = 2 * 5 = 10
  - HYPERVISOR Sockets = 1 * 5 = 5

**capacity-reconciliation-TC006 - Reconcile Subscription with Mixed Metrics**
- **Description**: Verify subscription with both Cores and Sockets
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Offering has cores=8, sockets=2, hypervisorCores=4, hypervisorSockets=1
  2. Subscription quantity=3
  3. Reconcile
- **Expected Results**:
  - 4 measurements created:
    - PHYSICAL Cores = 24
    - PHYSICAL Sockets = 6
    - HYPERVISOR Cores = 12
    - HYPERVISOR Sockets = 3

**capacity-reconciliation-TC007 - Update Existing Measurements**
- **Description**: Verify existing measurements are updated when offering changes
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Subscription has PHYSICAL Cores = 40
  2. Offering updated with cores=6
  3. Reconcile subscription
- **Expected Results**:
  - Existing measurement updated to new value (6 * quantity)
  - measurementsUpdated counter incremented
  - Log message indicates update

**capacity-reconciliation-TC008 - Create New Measurements**  
- **Description:** Verify new measurements are created  
- **Action:** `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`  
- **Test Steps:**
  1. Subscription has no measurements
  2. Reconcile with the offering that has capacity
- **Expected Results**:
  - New measurements created
  - measurementsCreated counter incremented

**capacity-reconciliation-TC009 - Delete Stale Measurements**
- **Description**: Verify removal of measurements no longer in offering
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Subscription has PHYSICAL Cores and HYPERVISOR Cores measurements
  2. Offering updates only to have PHYSICAL Cores (hypervisorCores = null)
  3. Reconcile
- **Expected Results**:
  - PHYSICAL Cores measurement retained
  - HYPERVISOR Cores measurement deleted
  - measurementsDeleted counter incremented

**capacity-reconciliation-TC010 - Null or Zero Capacity Values**
- **Description**: Verify measurements not created for null/zero values
- **Action**: `CapacityReconciliationService.reconcileCapacityForSubscription(SubscriptionEntity)`
- **Test Steps**:
  1. Offering has cores=null, sockets=0
  2. Reconcile subscription
- **Expected Results**:
  - No measurements created for null or zero values
  - Only non-null, positive values result in measurements

## Reconciliation Consumer (Kafka)

**capacity-reconciliation-kafka-TC001 - Process Reconciliation Task from Kafka**
- **Description**: Verify CapacityReconciliationConsumer processes tasks
- **Kafka Topic**: `platform.rhsm-subscriptions.capacity-reconcile`
- **Consumer**: `CapacityReconciliationConsumer.consume(ReconcileCapacityByOfferingTask)`
- **Test Steps**:
  1. Publish ReconcileCapacityByOfferingTask to the capacity_reconcile_task topic
  2. Verify the consumer picks up the message
- **Expected Results**:
  - Consumer receives task
  - reconcileCapacityForOffering called with sku, offset, limit from task
  - Log: "Capacity Reconciliation Worker is reconciling capacity for offering..."

**capacity-reconciliation-kafka-TC002 - Handle Malformed Reconciliation Task**
- **Description**: Verify error handling for invalid task messages
- **Kafka Topic**: `platform.rhsm-subscriptions.capacity-reconcile`
- **Consumer**: `CapacityReconciliationConsumer.consume(ReconcileCapacityByOfferingTask)`
- **Test Steps**:
  1. Publish malformed JSON to the topic
- **Expected Results**:
  - Consumer logs an error
  - Service remains stable
  - Other messages continue processing

## Capacity Metrics & Measurements
**capacity-metrics-TC001 - Cores Metric Calculation**
- **Description**: Verify Cores metric capacity
- **Test Steps**:
  1. Offering: cores=8
  2. Subscription: quantity=5
  3. Reconcile
- **Expected Results**:
  - PHYSICAL Cores measurement = 40

**capacity-metrics-TC002 - Sockets Metric Calculation**
- **Description**: Verify Sockets metric capacity
- **Test Steps**:
  1. Offering: sockets=4
  2. Subscription: quantity=10
  3. Reconcile
- **Expected Results**:
  - PHYSICAL Sockets measurement = 40

**capacity-metrics-TC003 - Hypervisor Cores**
- **Description**: Verify hypervisor cores calculation
- **Test Steps**:
  1. Offering: hypervisorCores=16
  2. Subscription: quantity=2
  3. Reconcile
- **Expected Results**:
  - HYPERVISOR Cores measurement = 32

**capacity-metrics-TC004 - Hypervisor Sockets**
- **Description**: Verify hypervisor sockets calculation
- **Test Steps**:
  1. Offering: hypervisorSockets=2
  2. Subscription: quantity=20
  3. Reconcile
- **Expected Results**:
  - HYPERVISOR Sockets measurement = 40

## Metered Cores
**capacity-metrics-TC005 - Decreasing metered Cores**
- **Description**: Verify that a metered capacity decreases when the cores quantity is decreased
- **Test Steps**:
  1. Given: Create a contract with Rosa (rosa) product that has Core:8
  2. When: Update the Cores to 4 and run Subscriptions sync
  3. Then: Verify that the cores are updated (decreased) and the capacity reflects the update sku
- **Expected Results**: 
  - the Cores will reflect 4 cores 

**capacity-metrics-TC006 - Increasing metered Cores**
- **Description**: Verify that a metered capacity increases when the core quantity is increased
- **Test Steps**:
  1. Given: Create a contract with a Rosa (rosa) product that has Core:4
  2. When: Update the Cores to 8 and run Subscriptions sync
  3. Then: Verify that the cores are updated (increased) and the capacity reflects the updated sku
- **Expected Results**: 
  - the Cores will reflect 8 cores 

## Non-metered cores
**capacity-metrics-TC007 - Decreasing Non metered cores**
- **Description**: Verify that a non-metered capacity decreases when the core quantity is decreased
- **Test Steps**:
  1. Given: create a subscription with OpenShift Container Platform (openshift-container-platform) Cores 8
  2. When: Update the cores to be 4 and run Subscription sync
  3. Then: Verify that the cores are updated (decreased) and the capacity reflects the updated sku
- **Expected Results**: 
  - the Cores will reflect the 4 cores

**capacity-metrics-TC008 - Increasing Non metered cores**
- **Description**: Verify that a non-metered capacity increases when the core quantity is increased
- **Test Steps**:
  1. Given: create a subscription with OpenShift Container Platform (openshift-container-platform) Cores 4
  2. When: Update the cores to be 8 and run Subscription sync
  3. Then: Verify that the cores are updated (increased) and the capacity reflects the updated sku
- **Expected Results**: 
  - the Cores will reflect the 8 cores

## Non-metered Sockets
**capacity-metrics-TC009 - Decreasing Non metered sockets**
- **Description**: Verify that a non-metered capacity decreases when the sockets quantity is decreased
- **Test Steps**:
  1. Given: create a subscription with Satellite Server product (satellite-server) sockets 8
  2. When: Update the sockets to be 4 and run Subscription sync
  3. Then: Verify that the sockets are updated (decreased) and the capacity reflects the updated sku
- **Expected Results**: 
  - the sockets will reflect the 4 sockets

**capacity-metrics-TC010 - Increasing Non metered sockets**
- **Description**: Verify that a RHEL for x86 (rhel-for-x86) capacity increases when the sockets quantity is increased
- **Test Steps**: 
  1. Given: create a subscription with sku sockets 4
  2. When: Update the RHEL for x86 (rhel-for-x86) sockets to be 8 and run Subscription sync/Reconcile (?)
  3. Then: Verify that the sockets are updated (increased) and the capacity reflects the updated sku
- **Expected Results**: 
  - the sockets will reflect the 8 sockets

## Accounts with Subscriptions and or contracts
**capacity-metrics-TC011 - Increasing subscription on an account with multiple contracts**
- **Description**: Verify that an account with multiple contracts and a subscription increases when the capacity quantity is increased
- **Test Steps**:
  1. Given: Create an account with two contracts and a subscription
  2. When: Increase the sku related to the subscription and run Subscription sync
  3. Then: Verify that the capacity increases
- **Expected Results**: 
  - that the capacity increases

**capacity-metrics-TC012 - Decreasing subscription on an account with multiple contracts**
- **Description**: Verify that an account with multiple contracts and a subscription decreases when the capacity quantity is decreased
- **Test Steps**:
  1. Given: Create an account with two contracts and a subscription
  2. When: Decrease the sku related to the subscription and run Subscription sync
  3. Then: Verify that the capacity decreases
- **Expected Results**: 
  - that the capacity decreases 

**capacity-metrics-TC013 - Increasing contract on an account with multiple subscriptions**
- **Description**: Verify that an account with multiple subscriptions and a contract increases when the capacity quantity is increased
- **Test Steps**:
  1. Given: Create an account with two subscriptions and a contract
  2. When: Increase the metrics related to the contract and run Subscription sync
  3. Then: Verify that the capacity increases
- **Expected Results**: 
  - that the capacity increases

**capacity-metrics-TC014 - Decreasing contract on an account with multiple subscriptions**
- **Description**: Verify that an Account with multiple subscriptions and a contract decreases when the capacity quantity is decreased
- **Test Steps**:
  1. Given: Create an account with two subscriptions and a contract
  2. When: Decrease the metrics related to the contract and run Subscription sync
  3. Then: Verify that the capacity decreases
- **Expected Results**: 
  - that the capacity decreases 

**capacity-metrics-TC015 - Increasing capacity with multiple subscriptions**
- **Description**: Verify that an account with multiple subscriptions and multiple contracts increases when the capacity quantity is increased
- **Test Steps**:
  1. Given: Create an account with two subscriptions and two contracts
  2. When: Increase the metrics related to one of the contracts, increase the sku related to one of the subscriptions and run Subscription sync
  3. Then: Verify that the capacity increases for both
- **Expected Results**: 
  - that the capacity increases for both

**capacity-metrics-TC016 - Decreasing capacity with multiple subscriptions**
- **Description**: Verify that an account with multiple subscriptions and multiple contracts decreases when the capacity quantity is decreased
- **Test Steps**:
  1. Given: Create an account with two subscriptions and two contracts
  2. When: Decrease the metrics related to the contract, decrease the sku related to one of the subscriptions and run Subscription sync
  3. Then: Verify that the capacity decreases for both
- **Expected Results**: 
  - that the capacity decreases for both 