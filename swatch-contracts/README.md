# swatch-contracts

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
../gradlew :swatch-contracts:quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only
> at http://localhost:8000/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
../gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into
the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

## Mapping Partner API with Swatch data

We enrich our data (contracts and subscriptions) from different third parties like Partner API and the Subscription API. 
In swatch-contracts, we trigger the enrichment when receiving events from the Partner Gateway, then 
we call again the Partner API to gather extra information <1> we need to move forward and 
finally, we call the Subscription API to find the subscription ID <2> that matches with the event <1> we received. 
Each of these sources are enumerated as follows:
<1> the response from Partner API is of type [PartnerEntitlements](https://github.com/RedHatInsights/rhsm-subscriptions/blob/5bce20986bb3c1b2750502db63efc694461cce57/clients/rh-partner-gateway-client/rh-partner-gateway-api-spec.yaml#L41)
<2> from the response from Subscription API, we simply extract the subscription ID. 

For testing purposes, we can use the POST endpoint `/api/swatch-contracts/internal/contracts` with payload:

```
{
  "partner_entitlement": <1>,
  "subscription_id": <2>
}
```

You can find more information about how to fill the request using the following table:

| Table            | Column               | Source                                                                                                                                                                          |
|------------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| contracts        | subscription_number  | <1>.rhEntitlements[*].subscriptionNumber                                                                                                                                        |
| contracts        | org_id               | <1>.rhAccountId                                                                                                                                                                 |
| contracts        | sku                  | <1>.rhEntitlements[*].sku                                                                                                                                                       |
| contracts        | start_date           | <1>.entitlementDates.startDate                                                                                                                                                  |
| contracts        | end_date             | <1>.entitlementDates.endDate                                                                                                                                                    |
| contracts        | vendor_product_code  | <1>.purchase.vendorProductCode                                                                                                                                                  |
| contracts        | billing_account_id   | if not null: <1>.partnerIdentities.customerAwsAccountId                                                                                                                         |
| ^                | ^                    | if not null: <1>.partnerIdentities.azureTenantId,<1>.partnerIdentities.azureSubscriptionId                                                                                      |
| ^                | ^                    | if not null: <1>.partnerIdentities.azureTenantId                                                                                                                                |
| contracts        | billing_provider_id  | if <1>.sourcePartner is "azure_marketplace", <br>then combination of <1>.purchase.azureResourceId,<1>.purchase.contracts[*].planId and <1>.purchase.vendorProductCode           |
| ^                | ^                    | if <1>.sourcePartner is "aws_marketplace", <br>then combination of <1>.purchase.vendorProductCode,<1>.partnerIdentities.awsCustomerId and <1>.partnerIdentities.sellerAccountId |
| contracts        | billing_provider     | <1>.sourcePartner                                                                                                                                                               |
| contract_metrics | metric_id            | <1>.purchase.contracts[*].dimensions[*].name                                                                                                                                    |
| contract_metrics | metric_value         | <1>.purchase.contracts[*].dimensions[*].value                                                                                                                                   |
| subscription     | subscription_id      | <2>                                                                                                                                                                             |

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

## Related Guides

- SmallRye Reactive Messaging ([guide](https://quarkus.io/guides/reactive-messaging)): Produce and
  consume messages and implement event driven and data streaming applications
- SmallRye Reactive Messaging - Kafka
  Connector ([guide](https://quarkus.io/guides/kafka-reactive-getting-started)): Connect to Kafka
  with Reactive Messaging
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplify
  your persistence code for Hibernate ORM via the active record or the repository pattern
- Liquibase ([guide](https://quarkus.io/guides/liquibase)): Handle your database schema migrations
  with Liquibase
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the
  PostgreSQL database via JDBC

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)

[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)

### Reactive Messaging codestart

Use SmallRye Reactive Messaging

[Related Apache Kafka guide section...](https://quarkus.io/guides/kafka-reactive-getting-started)

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
