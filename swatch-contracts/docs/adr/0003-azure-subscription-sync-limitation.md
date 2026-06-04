### 0003 - Azure Subscriptions Cannot Be Processed Through UMB Subscription Sync

* **Status:** Accepted
* **Deciders:** @jcarvaja
* **Date:** 2026-01-19

---

### Context

The SWATCH service has two primary flows for syncing subscription data:

1. **Subscription Sync Flow (UMB)**: Subscriptions are received via UMB messages and enriched by querying the Subscription IT Service (Search API) for additional details, including external references that contain marketplace billing information.
2. **Contract Sync Flow (Partner API)**: Contracts are synced directly from the IT Partner Gateway API, which provides comprehensive marketplace billing data for all cloud providers (AWS, Azure, GCP).

During testing, we discovered that Azure marketplace subscriptions cannot be properly processed through the subscription sync flow. The root cause is a limitation in the Subscription IT Service (Search API): it does not populate the `externalReferences` field for Azure subscriptions, even when explicitly requested with the `showExternalReferences=true` query parameter.

The Search API only returns external references for AWS Marketplace subscriptions. This is an intentional design decision by the IT Partner Gateway team, who reasoned that consumers requiring partner-specific information should retrieve it directly from the Partner Gateway API, as they are the authoritative source for this data.

Our subscription sync service (`SubscriptionSyncService`) relies on these external references to extract billing provider information. The utility class `SubscriptionDtoUtil` specifically looks for two keys in the external references map:

- `awsMarketplace` - for AWS Marketplace subscriptions
- `ibmmarketplace` - for IBM/Red Hat Marketplace subscriptions

The methods `extractBillingProviderId()`, `populateBillingProvider()`, and `extractBillingAccountId()` do not handle Azure external references because the Search API never provides them. As a result, when an Azure subscription is processed through the UMB subscription sync flow, no billing provider or billing account information can be extracted, and the subscription record is created with null billing references.

This limitation was confirmed in conversations with the Subscription IT Service team, who indicated that:
- External Gateway (EGW) does not publish the `externalReferences` object for Azure and GCP to the Subscription IT Service
- There was no consumer need identified when this decision was made, since SWATCH already integrates with the Partner Gateway API
- This decision could be revisited if a consumer need is demonstrated
- The complete information can be retrieved by querying the `partnerEntitlements` API in the Partner Gateway using the organization ID

### Decision

We accept this limitation and will **not** support processing Azure marketplace subscriptions through the UMB subscription sync flow. Azure subscriptions will only be synced through the contract sync flow (Partner API), where complete billing information is available.

The `SubscriptionDtoUtil` class will remain unchanged and will continue to only support extracting billing information for AWS and IBM Marketplace subscriptions.

Test plans and component tests have been updated to reflect this limitation:
- Test case `subscriptions-creation-TC003` has been modified to expect null billing references for Azure subscriptions processed via UMB
- Search API stubs have been updated to not provide external references for Azure subscriptions, accurately mimicking the behavior of the real Search API

### Consequences

* **Positive:**
    * **Clarity:** The limitation is now explicitly documented, preventing confusion in testing and future development.
    * **Realistic Testing:** Our component tests accurately reflect the behavior of production systems.
    * **Appropriate Flow Usage:** Azure subscriptions will continue to be properly processed through the contract sync flow (Partner API), which is the authoritative source for marketplace billing data.
    * **Avoids Unnecessary Complexity:** We avoid implementing workarounds or custom logic for a scenario that is not supported by the upstream service.

* **Negative:**
    * **Reduced Flexibility:** UMB subscription messages for Azure subscriptions will not contain complete billing information, limiting the usefulness of the subscription sync flow for Azure.
    * **Dependency on Contract Sync:** Azure marketplace subscriptions are fully dependent on the contract sync flow (Partner API) for proper billing information.
    * **Potential Future Work:** If the Subscription IT Service team decides to populate Azure external references in the future, we will need to update `SubscriptionDtoUtil` to support extracting this information.
    * **Testing Limitations:** We cannot fully test the end-to-end Azure subscription workflow through UMB messages alone.
