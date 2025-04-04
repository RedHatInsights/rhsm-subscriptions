# swatch-contracts

## Mapping Partner API with Swatch data

We enrich our data (contracts and subscriptions) from different third parties like Partner API and
the Subscription API.
In swatch-contracts, we trigger the enrichment when receiving events from the Partner Gateway, then
we call again the Partner API to gather extra information <1> we need to move forward and
finally, we call the Subscription API to find the subscription ID <2> that matches with the event <
1> we received.
Each of these sources are enumerated as follows:
<1> the response from Partner API is of
type [PartnerEntitlements](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5bce20986bb3c1b2750502db63efc694461cce57/clients/rh-partner-gateway-client/rh-partner-gateway-api-spec.yaml#L41)
<2> from the response from Subscription API, we simply extract the subscription ID.

For testing purposes, we can use the POST endpoint `/api/swatch-contracts/internal/contracts` with
payload:

```
{
  "partner_entitlement": <1>,
  "subscription_id": <2>
}
```

You can find more information about how to fill the request using the following table:

| Table            | Column              | Source                                                                                                                                                                          |
|------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| contracts        | subscription_number | <1>.rhEntitlements[*].subscriptionNumber                                                                                                                                        |
| contracts        | org_id              | <1>.rhAccountId                                                                                                                                                                 |
| contracts        | sku                 | <1>.rhEntitlements[*].sku                                                                                                                                                       |
| contracts        | start_date          | <1>.entitlementDates.startDate                                                                                                                                                  |
| contracts        | end_date            | <1>.entitlementDates.endDate                                                                                                                                                    |
| contracts        | vendor_product_code | <1>.purchase.vendorProductCode                                                                                                                                                  |
| contracts        | billing_account_id  | if not null: <1>.partnerIdentities.customerAwsAccountId                                                                                                                         |
| ^                | ^                   | if not null: <1>.partnerIdentities.azureTenantId,<1>.partnerIdentities.azureSubscriptionId                                                                                      |
| ^                | ^                   | if not null: <1>.partnerIdentities.azureTenantId                                                                                                                                |
| contracts        | billing_provider_id | if <1>.sourcePartner is "azure_marketplace", <br>then combination of <1>.purchase.azureResourceId,<1>.purchase.contracts[*].planId and <1>.purchase.vendorProductCode           |
| ^                | ^                   | if <1>.sourcePartner is "aws_marketplace", <br>then combination of <1>.purchase.vendorProductCode,<1>.partnerIdentities.awsCustomerId and <1>.partnerIdentities.sellerAccountId |
| contracts        | billing_provider    | <1>.sourcePartner                                                                                                                                                               |
| contract_metrics | metric_id           | <1>.purchase.contracts[*].dimensions[*].name                                                                                                                                    |
| contract_metrics | metric_value        | <1>.purchase.contracts[*].dimensions[*].value                                                                                                                                   |
| subscription     | subscription_id     | <2>                                                                                                                                                                             |

You can find a full example as follows:

```
curl -v -X POST http://localhost:8000/api/swatch-contracts/internal/contracts \
-H 'Content-Type: application/json' \
--data-binary @- << EOF
{
  "partner_entitlement": {
    "rhAccountId": "123456",
    "sourcePartner": "aws_marketplace",
    "entitlementDates": {
      "startDate": "2023-05-24T18:46:05Z",
      "endDate": "2023-05-24T20:46:06.273014Z"
    },
    "rhEntitlements": [
      {
        "subscriptionNumber": "12585274",
        "sku": "MW01485"
      }
    ],
    "purchase": {
      "vendorProductCode": "AAAA",
      "contracts": [
        {
          "planId": "<only for azure>",
          "endDate": "2023-05-24T20:46:06.273014Z",
          "dimensions": [
            {
              "name": "Cores",
              "value": "8"
            }
          ]
        }
      ]
    },
    "partnerIdentities": {
      "awsCustomerId": "BBB",
      "sellerAccountId": "CCC",
      "customerAwsAccountId": "DDD"
    }
  },
  "subscription_id": "123456456"
}
EOF
```

Output:

```json
{
  "status": {
    "status": "SUCCESS",
    "message": "New contract created"
  },
  "contract": {
    "uuid": "baa1dc70-9912-490b-94b6-76ffa67e6ca0",
    "subscription_number": "12585274",
    "sku": "MW01485",
    "start_date": "2023-05-24T18:46:05Z",
    "end_date": "2023-05-24T20:46:06.273014Z",
    "org_id": "123456",
    "billing_provider": "aws",
    "billing_provider_id": "AAAA;BBB;CCC",
    "billing_account_id": "DDD",
    "product_id": "OpenShift-metrics",
    "vendor_product_code": "ezcoaphi4bqc7lktoy2qfd6zi"
  }
}
```

# System Interactions

## Messaging Channels

### Kafka

| Direction | Channel                   | Topic                                                |
|-----------|---------------------------|------------------------------------------------------|
| Incoming  | `subscription-sync-task`  | `platform.rhsm-subscriptions.subscription-sync-task` |
| Incoming  | `capacity-reconcile-task` | `platform.rhsm-subscriptions.capacity-reconcile`     |
| Incoming  | `offering-sync-task`      | `platform.rhsm-subscriptions.offering-sync`          |
| Incoming  | `export-requests`         | `platform.export.requests`                           |
| Outgoing  | `enabled-orgs`            | `platform.rhsm-subscriptions.enabled-orgs-for-tasks` |
| Outgoing  | `capacity-reconcile`      | `platform.rhsm-subscriptions.capacity-reconcile`     |
| Outgoing  | `offering-sync`           | `platform.rhsm-subscriptions.offering-sync`          |

### UMB

| Direction | Channel                 | UMB Queue / VirtualTopic                            |
|-----------|-------------------------|-----------------------------------------------------|
| Incoming  | `contracts`             | `VirtualTopic.services.partner-entitlement-gateway` |
| Incoming  | `offering-sync-umb`     | `VirtualTopic.canonical.operationalProduct`         |
| Incoming  | `subscription-sync-umb` | `VirtualTopic.canonical.subscription`               |

---

## External REST Clients

| Service Name     | Base URL (example)                                                                              | Notes                       |
|------------------|-------------------------------------------------------------------------------------------------|-----------------------------|
| Partner API      | `${ENTITLEMENT_GATEWAY_URL}` e.g., `https://ibm-entitlement-gateway...`                         | Uses keystore/truststore    |
| Subscription API | `${SUBSCRIPTION_URL}` e.g., `https://subscription.stage.api.redhat.com/svcrest/subscription/v5` |                             |
| Product API      | `${PRODUCT_URL}` e.g., `https://product.api.redhat.com/svcrest/product/v3`                      | Uses keystore/truststore    |
| RBAC Service     | `${RBAC_ENDPOINT}` e.g., `/api/rbac/v1`                                                         | TLS options via clowder     |
| Export Service   | `${clowder.private-endpoints.export-service-service.url}`                                       | Requires `x-rh-exports-psk` |

---

## Database Configuration

| Property       | Value                                                                      |
|----------------|----------------------------------------------------------------------------|
| DB Kind        | `postgresql`                                                               |
| JDBC URL       | `jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_DATABASE}` |
| Credentials    | `DATABASE_USERNAME` / `DATABASE_PASSWORD`                                  |
| Migrations     | Liquibase (enabled via `quarkus.liquibase.migrate-at-start=true`)          |
| Schema Objects | Tables: `DATABASECHANGELOG`, `DATABASECHANGELOGLOCK` (customized)          |

> Also supports in-memory H2 configuration for `%test` profile.

---

## Splunk Logging

| Property                               | Description                                    |
|----------------------------------------|------------------------------------------------|
| `ENABLE_SPLUNK_HEC`                    | Enables Splunk HEC log handler                 |
| `SPLUNK_HEC_URL`                       | Default: `https://splunk-hec.redhat.com:8088/` |
| `SPLUNK_SOURCE` / `SPLUNK_SOURCE_TYPE` | Customizable log source + type                 |
| `SPLUNKMETA_namespace`                 | Metadata tagging for Splunk logs               |
| `quarkus.log.handler.splunk.*`         | Separate config for access logs + app logs     |

---