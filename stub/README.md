
Deploy wiremock and service to existing namespace

```
oc process -f wiremock.yaml | oc apply -f - 
```

Make the pod accessible to interact with the wiremock admin api
```
oc port-forward service/wiremock 8101
```

Use HTTP to create stubs.  Easiest way seems to use "jsonBody" instead of "bodyFileName" like our PR instructions usually have.

The below block is all you need in a .http file if you're using IntelliJ's HTTP client.  If you're using something else, need to be very careful with syntax and escape things appropriately.
```http
POST http://localhost:8101/__admin/mappings/new

{
    "priority": 1,
    "request": {
        "urlPath": "/mock/partnerApi/v1/partnerSubscriptions",
        "method": "POST"
    },
    "response": {
        "status": 200,
        "jsonBody": {
            "content": [
                {
                    "rhAccountId": "9999999",
                    "sourcePartner": "azure_marketplace",
                    "partnerIdentities": {
                        "azureSubscriptionId": "fa650050-dedd-4958-b901-d8e5118c0a5f",
                        "azureTenantId": "64dc69e4-d083-49fc-9569-ebece1dd1408",
                        "azureCustomerId": "eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31"
                    },
                    "rhEntitlements": [
                        {
                            "sku": "BASILISK",
                            "redHatSubscriptionNumber": "1111111"
                        }
                    ],
                    "purchase": {
                        "vendorProductCode": "rh-rhel-sub-preview",
                        "azureResourceId": "a69ff71c-aa8b-43d9-dea8-822fab4bbb86",
                        "contracts": [
                            {
                                "startDate": "2023-06-09T13:59:43.035365Z",
                                "planId": "rh-rhel-sub-1yr",
                                "dimensions": [
                                    {
                                        "name": "vCPU",
                                        "value": 4
                                    }
                                ]
                            }
                        ]
                    },
                    "status": "UNSUBSCRIBED",
                    "entitlementDates": {
                        "startDate": "2023-06-09T13:59:43.035365Z",
                        "endDate": "2023-06-09T19:37:46.651363Z"
                    }
                }
            ],
            "page": {
                "size": 0,
                "totalElements": 0,
                "totalPages": 0,
                "number": 0
            }
        },
        "headers": {
            "Content-Type": "application/json"
        }
    }
}

###
```
