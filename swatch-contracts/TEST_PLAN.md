# 1\. Introduction

The s**watch-contracts** module is a critical service within the Subscription Watch platform that serves as the central hub for managing Red Hat subscription contracts purchased through cloud marketplaces (AWS and Azure). It acts as the authoritative system for tracking Pay-As-You-Go (PAYG) subscription contracts, their associated metrics, and billing information. The service also handles regular non-PAYG subscriptions, as evidenced by the test cases managing all subscription types. This comprehensive management enables accurate capacity reporting and usage-based billing for Red Hat products sold via cloud marketplace partnerships.

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

# 2\. Test Strategy

This test plan focuses on covering the test scenario for component-level tests, utilizing the new Java component test framework.

**Test Approach:**

* The testing will follow a **risk-based approach**, prioritizing test cases for critical functionality and high-risk areas.
* **Automated tests** will be developed for component-level checks on the new Java component test framework, while integration and end-to-end testing will be covered in another document and developed on iqe-rhsm-subscription-plugin.

**Test Data Strategy:**

The scenarios will be simulated using mocked data.

# 3\. Test Scenarios

## 3.1 Test Cases at API Level:

### 3.1.1 Contract Creation via Kafka Messages

**contracts-creation-TC001 \- Process a valid PAYG contract with one valid dimension for AWS Marketplace**  
- **Description**: Verify that an AWS PAYG contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic \`contracts\` is available  
  - Prepare a valid AWS partner entitlement message  
- **Action:**  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - billing\_provider: "aws"  
  - billing\_provider\_id follows format: "{vendorProductCode};{awsCustomerId};{sellerAccountId}"  
  - billing\_account\_id contains customerAwsAccountId

**contracts-creation-TC002 \- Process a valid PURE PAYG contract (without dimensions) for AWS Marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:**   
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic \`contracts\` is available  
  - Prepare a valid AWS partner entitlement message  
- **Action:**     
  - Publish message to Kafka topic  
- **Verification:**   
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)

**contracts-creation-TC003 \- Process a valid PAYG contract with one valid dimension for the Azure Marketplace**  
- **Description**: Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic available  
  - Prepare a valid Azure partner entitlement message  
- **Action**:  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - \`billing\_provider=azure\`  
  - \`billing\_provider\_id\` formatted as \`{azureResourceId};{planId};{vendorProductCode}\`  
  - \`billing\_account\_id\` contains azureTenantId

**contracts-creation-TC004 \-** **Process a valid PURE PAYG contract (without dimensions) for the Azure Marketplace**  
- **Description**: Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic available  
  - Prepare a valid Azure partner entitlement message  
- **Action**:  
  - Publish message to Kafka topic  
- **Verification**:   
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - \`billing\_provider=azure\`  
  - \`billing\_provider\_id\` formatted as \`{azureResourceId};{planId};{vendorProductCode}\`  
  - \`billing\_account\_id\` contains azureTenantId

**contracts-creation-TC005 \- Process contract with multiple metrics/dimensions**  
- **Description**: Verify contracts can store multiple metrics from partner entitlement dimensions.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic available  
  - Prepare a valid partner entitlement message with multiple metrics/dimensions  
- **Action**:  
  - Publish message to Kafka topic with multiple metrics/dimensions  
- **Verification**:  
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify contract exists with correct fields  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Contract object contains multiple metrics/dimensions

**contracts-creation-TC006 \- Process contract with multiple metrics/dimensions (WITH AN INVALID ONE)**  
- **Description**:  Verify contracts with multiple metrics/dimensions where one of those metrics is an invalid metric, and generate a valid contract with the valid metric.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic available  
  - Prepare a valid partner entitlement message with multiple metrics/dimensions where one of those metrics is invalid.  
- **Action**:  
  - Publish message to Kafka topic with multiple metrics/dimensions  
- **Verification**:  
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Verify the contract exists with the correct fields without the invalid metric.  
- **Expected Result**:  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Contract object contains only valid metrics

**contracts-creation-TC007 \- Process contract message with missing required fields shouldn’t persist**  
- **Description**: Verify validation errors are handled gracefully.  
- **Setup**:   
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic available  
  - Prepare an invalid partner entitlement message with missing required fields.  
- **Action**:  
  - Publish message to Kafka topic with missing required fields  
- **Verification**:  
  - Check error response.  
  - Query contract via internal API: GET /internal/contracts?org\_id=org123  
  - Ensure the contract was not created  
- **Expected Result**:  
  - HTTP 400 Bad Request  
  - Error message indicates a missing required field

### 3.1.2  Contract Creation via Internal API

**contracts-creation-TC008** \- **Create a valid PAYG contract with one valid dimension for AWS marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to \`/api/swatch-contracts/internal/contracts\` with a valid \`ContractRequest\` payload including partner\_entitlement, metrics, and subscription\_id.  
- **Verification:** Check response status and returned contract object.  
  - **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - billing\_provider: "aws"  
  - billing\_provider\_id follows format: "{vendorProductCode};{awsCustomerId};{sellerAccountId}"  
  - billing\_account\_id contains customerAwsAccountId

**contracts-creation-TC009** \- **Create a valid PURE PAYG contract (without dimensions) for AWS marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to \`/api/swatch-contracts/internal/contracts\` with a valid \`ContractRequest\` payload including partner\_entitlement, metrics, and subscription\_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  \- Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)

**contracts-creation-TC010** \- **Create a valid PAYG contract with one valid dimension for the Azure marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to \`/api/swatch-contracts/internal/contracts\` with a valid \`ContractRequest\` payload including partner\_entitlement, metrics, and subscription\_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - billing\_provider: "azure"  
  - billing\_provider\_id follows Azure format  
  - billing\_account\_id populated correctly

**contracts-creation-TC011** \- **Create a valid PURE PAYG contract (without dimensions) for the Azure marketplace**  
- **Description:** Verify that a contract can be successfully created with valid partner entitlement data, metrics, and subscription ID.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to \`/api/swatch-contracts/internal/contracts\` with a valid \`ContractRequest\` payload including partner\_entitlement, metrics, and subscription\_id.  
- **Verification:** Check response status and returned contract object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)

**contracts-creation-TC012** \- **Create contract with multiple metrics/dimensions**  
- **Description**: Verify contracts can store multiple metrics from partner entitlement dimensions.  
- **Setup:** Prepare entitlement with multiple dimensions (e.g., cpu-hours, instance-hours).  
- **Action:** POST contract with multiple dimensions in purchase.contracts\[\*\].dimensions.  
- **Verification:** Query created contract.  
- **Expected Result:**  
  - All metrics are stored  
  - Each metric has a correct metric\_id and value

**contracts-creation-TC013** \- **Create contract with multiple metrics/dimensions (WITH AN INVALID ONE)**  
- **Description**: Verify contracts with multiple metrics/dimensions where one of those metrics is an invalid metric, and generate a valid contract with the valid metric.  
- **Setup:** Prepare entitlement with multiple dimensions with one invalid (e.g., cpu-hours, ins-hours).  
- **Action:** POST contract with multiple dimensions in purchase.contracts\[\*\].dimensions.  
- **Verification:** Query created contract.  
- **Expected Result:**  
  - Only valid metrics are stored  
  - Each metric stored has a correct metric\_id and value

**contracts-creation-TC014** \- **Create contract with missing required fields shouldn’t persist**  
- **Description:** Verify validation for missing required fields.  
- **Setup:** Prepare incomplete contract request.  
- **Action:** POST contract missing partner\_entitlement, or subscription\_id.  
- **Verification:** Check error response.  
- **Expected Result:**  
  - HTTP 400 Bad Request  
  - Error message indicates a missing required field

### 3.1.3 Contract Retrieval

**contracts-retrieval-TC001** \- **Get contracts by org\_id**  
- **Description:** Verify contracts can be retrieved by organization ID.  
- **Setup:** Create multiple contracts for org "org123" and "org456".  
- **Action:** GET \`/api/swatch-contracts/internal/contracts?org\_id=org123\`.  
- **Verification:** Check the returned contract list.  
  - **Expected Result:**  
  - Only contracts for org123 are returned  
  - All contracts have the correct org\_id

**contracts-retrieval-TC002** \- **Get contracts active at a specific timestamp**  
- **Description:** Verify temporal filtering using the timestamp parameter.  
- **Setup:** Create contracts with different start\_date and end\_date ranges.  
- **Action:** GET contracts with a timestamp parameter set to a specific date.  
- **Verification:** Validate temporal filtering.  
- **Expected Result:**  
  - Only contracts active at the specified timestamp are returned  
  - Contracts where start\_date \<= timestamp \< end\_date

**contracts-retrieval-TC003** \- **Get contracts by billing\_provider**  
- **Description:** Verify filtering by billing provider (AWS/Azure).  
- **Setup:** Create contracts with AWS and Azure billing providers.  
- **Action:** GET contracts with \`billing\_provider=aws\`.  
- **Verification:** Check response.  
- **Expected Result:**  
  - Only AWS contracts returned  
  - All have billing\_provider: "aws"

**contracts-retrieval-TC004 \- Get contracts with multiple parameters**  
- **Description**: Verify multiple filter parameters on the same request.  
- **Setup**: Create contracts.  
- **Action**: GET contracts with multiple parameters (timestamp, org\_id, billing\_provider).  
- **Verification**: Validate all filters.  
- **Expected** **Result**:  
  - Only contracts matching the multiple parameters should be returned.

**contracts-retrieval-TC005** \- **Get contracts with no results in a specific time range**  
- **Description:** Verify behavior when no contracts match the time range criteria.  
- **Setup:** Create contracts with start\_date and end\_date ranges.  
- **Action:** GET contracts with a different time range from the one used on the contract creation.  
- **Verification:** Check response.  
- **Expected Result:**  
  - HTTP 200 with empty array  
  - No errors

**contracts-retrieval-TC006** \- **Get contracts with no results**  
- **Description:** Verify behavior when no contracts match the criteria.  
- **Setup:** Ensure no contracts exist for org\_id.  
- **Action:** GET contracts for non-existent org.  
- **Verification:** Check response.  
- **Expected Result:**  
  - HTTP 200 with empty array  
  - No errors

### 3.1.4 Contract Update via Kafka

**contracts-update-TC001 \- Process contract update message (existing contract)**  
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
  - StatusResponse: "EXISTING\_CONTRACTS\_SYNCED"  
  - End date updated to new value

**contracts-update-TC002 \- Process redundant contract message**  
- **Description**: Verify idempotency \- same message sent twice doesn't duplicate.  
- **Setup**: Send identical message twice  
- **Action**:\* Publish the same contract message twice  
- **Verification**: Check the database for a single contract only  
- **Expected Result**:  
  - First message creates a contract  
  - Second message: StatusResponse "REDUNDANT\_MESSAGE\_IGNORED"  
  - No duplicate contracts created

### 3.1.5 Contract Update via API

**contracts-update-TC003 \- Update contract start and end date**  
- **Description**: Verify updating the contract start and end date.  
- **Setup:**  
  - Create a contract with:   
  - subscription\_number: "12585274"  
  - start\_date: "2024-01-01T00:00:00Z"  
  - end\_date: "2024-12-31T23:59:59Z"  
  - Cores: 8  
- **Action:** Update the contract start and end date.  
- **Verification:**   
  - Query contract by subscription\_number and verify changes  
  - Check last\_updated timestamp  
  - Check the start date  
  - Check the end date  
- **Expected Result:**  
  - Existing contract located by subscription\_number \+ start\_date match  
  - start\_date update  
  - end\_date updated   
  - last\_updated timestamp updated to the current time  
  - UUID remains the same (no new contract created)  
  - Other fields unchanged (org\_id, sku, metrics)  
  - Response status: "EXISTING\_CONTRACTS\_SYNCED"

**contracts-update-TC004 \- Update contract end date (renewal)**  
- **Description**: Verify updating the contract end date ( with a date in the future ) when the customer renews the subscription.  
- **Setup:**  
  - Create a contract with:   
  - subscription\_number: "12585274"  
  - start\_date: "2024-01-01T00:00:00Z"  
  - end\_date:  "2024-12-31T23:59:59Z"  
  - Cores: 8  
- **Action:** Update the contract end-date with a date in the future ("2025-12-31T23:59:59Z") 
- **Verification:**   
  - Query contract by subscription\_number and verify changes  
  - Check last\_updated timestamp  
  - Check end-date  
- **Expected Result:**  
  - Existing contract located by subscription\_number \+ start\_date match  
  - end\_date updated from "2024-12-31T23:59:59Z" to "2025-12-31T23:59:59Z"  
  - last\_updated timestamp updated to the current time  
  - UUID remains the same (no new contract created)  
  - Other fields unchanged (org\_id, sku, metrics)  
  - Response status: "EXISTING\_CONTRACTS\_SYNCED"

**contracts-update-TC005 \- Update contract metrics for Payg**   
- **Description**: Verify updating contract metrics when the customer upgrades the tier.  
- **Setup**:  
  - Create a contract with Cores: 8, Instance-hours: 100  
- **Action**: Submit updated entitlement with Cores: 16, Instance-hours: 200  
- **Expected Result**:  
  - Existing contract found and updated  
  - Cores metric updated: 8 → 16  
  - Instance-hours metric updated: 100 → 200  
  - last\_updated timestamp changed  
  - contract.status.message \== "Existing contracts and subscriptions updated"  
  - contract.status.status \== "SUCCESS"

**contracts-update-TC006 \- Update contract metrics from pure payg(no metrics before) to payg(with metrics)**  
- **Description**: Verify updating contract metrics when the customer upgrades the tier.  
- **Setup**:  
  - Create a contract with Cores: 8, Instance-hours: 100  
- **Action**: Submit updated entitlement with Cores: 16, Instance-hours: 200  
- **Expected Result**:  
  - Existing contract found and updated  
  - Cores metric updated: 8 → 16  
  - Instance-hours metric updated: 100 → 200  
  - last\_updated timestamp changed  
  - contract.status.message \== "Existing contracts and subscriptions updated"  
  - contract.status.status \== "SUCCESS"

**contracts-update-TC007 \- Update contract \- terminate (set end date)**  
- **Description**: Verify terminating contract by setting end date to current time.  
- **Setup**:  
  - Create a contract with an end date in the future date  
- **Action**: Submit entitlement with end\_date: current timestamp  
- **Expected** **Result**:  
  - end\_date set to termination timestamp  
  - Contract becomes inactive  
  - Subscription end\_date also updated

**contracts-update-TC008 \- Update contract \- add new metric**  
- **Description:** Verify adding a new metric to an existing contract.  
- **Setup**:  
  - Create a contract with one metric.  
- **Action**: Update the contract with another metric  
- **Expected** **Result**:  
  - Existing contract found and updated  
  - Old metric remains  
  - New metric added  
  - last\_updated timestamp changed  
  - contract.status.message \== "Existing contracts and subscriptions updated"  
  - contract.status.status \== "SUCCESS"

**contracts-update-TC009 \- Update contract \- remove metric**  
- **Description**: Verify that a metric is removed from an existing contract.  
- **Setup**:  
  - Create a contract with multiple metrics.  
- **Action**: Remove one metric from the contract.  
- **Expected** **Result**:  
  - Existing contract found and updated  
  - The specified metric was removed from the contract  
  - The metric that was not removed from the contract remains unchanged  
  - last\_updated timestamp changed  
  - contract.status.message \== "Existing contracts and subscriptions updated"  
  - contract.status.status \== "SUCCESS"

### 3.1.6 Contract Deletion

**contracts-deletion-TC001** \- **Delete contract by UUID**  
- **Description:** Verify hard deletion of contract and its metrics.  
- **Setup:** Create a contract and note its UUID.  
- **Action:** DELETE \`/api/swatch-contracts/internal/contracts/{uuid}\`.  
- **Verification:** Attempt to retrieve deleted contract.  
- **Expected Result:**  
  - HTTP 200 response  
  - Contract no longer retrievable  
  - Associated metrics also deleted

**contracts-deletion-TC002** \- **Delete non-existent contract**  
- **Description:** Verify error handling for deleting a non-existent UUID.  
- **Setup:** Generate a random UUID that doesn't exist.  
- **Action:** DELETE contract with invalid UUID.  
- **Verification:** Check error response.  
- **Expected Result:**  
  - HTTP 404 Not Found or appropriate error  
  - Error message indicates contract not found

### 

### 3.1.7 Contract Sync

**contracts-sync-TC001 \- Sync contracts for a single organization**  
- **Description**: Verify contract sync triggers for a specific org.  
- **Setup**: Have upstream contracts available for the org.  
- **Action**: POST \`/api/swatch-contracts/internal/rpc/sync/contracts/{org\_id}\`.  
- **Verification**: Check contracts are synced.  
- **Expected Result**:  
  - HTTP 200 with StatusResponse  
  - Contracts from upstream are created/updated  
  - Status: "Success"

**contracts-sync-TC002 \- Sync with delete contracts and subscriptions**  
- **Description**: Verify the delete\_contracts\_and\_subs parameter.  
- **Setup**: Create contracts and subscriptions for org.  
- **Action**: POST sync with \`delete\_contracts\_and\_subs=true\`.  
- **Verification**: Check database state.  
  - **Expected Result**:  
  - All contracts deleted before sync  
  - All PAYG subscriptions deleted before sync  
  - Fresh sync performed

**contracts-sync-TC003 \- Sync all contracts across all organizations**  
- **Description**: Verify syncAllContracts triggers sync for all orgs with contracts.  
- **Setup**: Have multiple orgs with contracts.  
- **Action**: POST \`/internal/rpc/syncAllContracts\`.  
- **Verification**: Monitor sync progress.  
- **Expected Result**:  
  - HTTP 202 Accepted  
  - StatusResponse: "All Contracts are Synced"  
  - Each org's contracts synced

**contracts-sync-TC004 \- Sync when no contracts exist**  
- **Description**: Verify sync all when no active contracts are found.  
- **Setup**: Ensure no contracts.  
- **Action:** POST syncAllContracts.  
- **Verification**: Check response.  
- **Expected Result**:  
  - Status: "No active contract found for the orgIds"  
  - No errors

**contracts-sync-TC005** **\- Sync subscriptions for contracts by org**  
- **Description**: Verify subscription sync for all contracts of an org.  
- **Setup**: Have contracts for org without subscriptions.  
- **Action**: POST \`/api/swatch-contracts/internal/rpc/sync/contracts/{org\_id}/subscriptions\`.  
- **Verification**: Check subscriptions are created.  
  - **Expected Result**:  
  - StatusResponse success  
  - Subscriptions synced from Subscription API

**contracts-sync-TC006 \- Clear all contracts for the organization**  
- **Description**: Verify that deleteContractsByOrg removes all org contracts.  
- **Setup**: Create multiple contracts for the org.  
- **Action**: DELETE \`/api/swatch-contracts/internal/rpc/reset/contracts/{org\_id}\`.  
- **Verification**: Query contracts for org.  
- **Expected Result**:  
  - HTTP 200 StatusResponse  
  - Status: "Contracts Cleared for given org\_id"  
  - No contracts remain for org\_id

### 3.1.8 Subscription Management via Kafka

**subscriptions-creation-TC001 \- Process a valid UMB subscription XML message from Kafka**  
- **Description**: Verify subscription creation via UMB XML Kafka message.  
- **Setup**:  
  - Ensure \`UMB\_ENABLED=true\`  
  - Kafka topic \`SUBSCRIPTION\_SYNC\_TASK\_UMB\` available  
- **Action**:  
  - Publish message to SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification**:  
  - Query subscription via internal API  
  - Verify subscription created  
- **Expected Result**:  
- XML parsed successfully via \`CanonicalMessage.createMapper()\`  
- Subscription entity created for org  
- subscription\_number  
- quantity  
- sku  
- Start and end dates are correctly parsed  

**subscriptions-creation-TC002 \- Process UMB subscription with AWS external references**  
- **Description**: Verify AWS marketplace subscription data extraction from UMB.  
- **Action**:  
  - Publish message to SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification**: Query subscription and check AWS fields  
- **Expected Result**:  
  - Subscription created with AWS external references  
  - billing\_provider  
  - billing\_provider\_id contains AWS identifiers  
  - billing\_account\_id

**subscriptions-creation-TC003 \- Process UMB subscription with Azure external references**  
- **Description**: Verify Azure marketplace subscription data from UMB.  
- **Action**:  
  - Publish message to SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification**: Check Azure-specific fields  
- **Expected Result**:  
- Subscription created with Azure references  
- \`billing\_provider=azure\`  
- \`billing\_account\_id\` contains Azure tenant ID

**subscriptions-creation-TC004 \- Process malformed UMB XML message**  
- **Description**: Verify error handling for invalid XML.  
- **Action**:  
  - Publish malformed UMB XML message to SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification**: Subscription not created  
- **Expected Result**:  
  - \`JsonProcessingException\` thrown (XML parsing error)  
  - No subscription created  
  - Message handling fails gracefully

**subscriptions-creation-TC005 \- Process UMB message with missing required fields**  
- **Description**: Verify validation for incomplete subscription data.  
- **Action**:  
  - Publish the UMB message  with missing required fields SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification**: Check for validation errors  
- **Expected Result**:  
  - Validation failure or graceful error handling  
  - No subscription created with incomplete data  
  - Error logged with details

**subscriptions-creation-TC006 \- Process subscription update via UMB**  
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

**subscriptions-creation-TC007 \- Process UMB message with multiple products/SKUs**  
- **Description**: Verify a single subscription with multiple product SKUs.  
- **Action**: Publish a subscription with multiple products/SKUs to the SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
- **Verification:** Check subscription has multiple product associations  
- **Expected Result**:  
  - Single subscription created  
  - Multiple SKUs associated (parent \+ children)  
  - All products stored correctly

**subscriptions-creation-TC008 \- Process terminated subscription via UMB**  
- **Description**: Verify subscription termination messages.  
- **Action**:  
  - Publish message to SUBSCRIPTION\_SYNC\_TASK\_UMB Kafka topic  
  - Update the end date to the current timestamp  
- **Verification**: Check subscription end\_date updated  
- **Expected Result**:  
  - Subscription marked as terminated  
  - \`end\_date\` set to termination date  
  - Status reflects termination

### 3.1.9 Subscription Management via API

**subscriptions-creation-TC009** \- **Create a valid PAYG contract and verify the Contract/Subscription table**  
- **Description:** Verify the contract/subscription after a contract/subscription creation.  
- **Setup:** Ensure partner entitlement data, metrics, and subscription ID are available.  
- **Action:** POST to \`/api/swatch-contracts/internal/contracts\`   
- **Verification:** Check response status and returned contract/subscription object.  
- **Expected Result:**  
  - HTTP 200 response  
  - Response contains status.status: "SUCCESS"  
  - Contract object contains all expected fields (uuid, subscription\_number, sku, start\_date, end\_date, org\_id, billing\_provider, etc.)  
  - Validate  
  - Contract/subscription table.

**subscriptions-creation-TC010** \- **Save subscriptions PURE PAYG**  
- **Description:** Verify subscription saving when enabled.  
- **Setup:** Prepare subscriptions with JSON array  
- **Action:**   
  - POST \`/api/swatch-contracts/internal/subscriptions\` with JSON array.  
  - Sync subscriptions  
- **Verification:** Query saved subscriptions.  
  - Expected Result:  
  - SubscriptionResponse: "Success"  
  - Subscriptions persisted  
  - Multiple subscriptions created from an array  
- **Note:** This endpoint **SUPPORTS multiple subscriptions** via JSON array

**subscriptions-creation-TC003** \- **Save subscriptions PAYG**  
- **Description:** Verify subscription saving when enabled.  
- **Setup:** Prepare subscriptions with JSON array  
- **Action:**   
  - POST \`/api/swatch-contracts/internal/subscriptions\` with JSON array.  
  - Sync subscriptions  
- **Verification:** Query saved subscriptions.  
  - Expected Result:  
  - SubscriptionResponse: "Success"  
  - Subscriptions persisted  
  - Multiple subscriptions created from an array  
- **Note:** This endpoint **SUPPORTS multiple subscriptions** via JSON array

**subscriptions-sync-TC001 \- Sync all subscriptions for enabled orgs**  
- **Description**: Verify PUT /rpc/subscriptions/sync enqueues org subscriptions.  
- **Setup**: Configure sync-enabled orgs.  
- **Action:** PUT \`/api/swatch-contracts/internal/rpc/subscriptions/sync\`.  
- **Verification**: Monitor sync queue.  
- **Expected Result:**  
  - RpcResponse with success  
  - Sync tasks enqueued for each org

**subscriptions-sync-TC002** \- **Sync UMB subscription XML message**  
- **Description:** Verify processing of UMB CanonicalMessage XML.  
- **Setup:** Prepare a valid UMB subscription XML.  
 **Action:**   
  - POST \`/api/swatch-contracts/internal/subscriptions/umb\` with XML.  
  - Sync subscriptions  
- **Verification:** Check subscription created.  
- **Expected Result:**  
  - XML parsed correctly  
  - Subscription entity created  
  - SubscriptionResponse: "Success"

**subscriptions-termination-TC001** \- **Terminate subscription with timestamp**  
- **Description:** Verify manual subscription termination.  
- **Setup:** Create an active subscription.  
- **Action:** POST \`/api/swatch-contracts/internal/subscriptions/terminate/{subscription\_id}?timestamp=2024-01-01T00:00:00Z\`.  
- **Verification:** Check subscription end date.  
- **Expected Result:**  
  - TerminationRequest with message  
  - Subscription end\_date set to timestamp  
  - Subscription effectively terminated

### 3.1.10 Capacity Reports

**capacity-report-TC001** \- **Get V2 SKU capacity report**  
- **Description:** Verify the V2 endpoint with an enhanced measurement array.  
- **Setup:** Create subscriptions with multiple metrics.  
- **Action:** GET /api/rhsm-subscriptions/v2/subscriptions/products/rhel  
- **Verification:** Check V2 structure.  
- **Expected Result:**  
  - SkuCapacityReport\_V2 returned  
  - The measurements field is an array of doubles, matching the received values (e.g., \`\[8.0, 100.0\]\`)  
  - Meta includes measurements array with metric names (e.g., \`\["Cores", "Instance-hours"\]\`)

