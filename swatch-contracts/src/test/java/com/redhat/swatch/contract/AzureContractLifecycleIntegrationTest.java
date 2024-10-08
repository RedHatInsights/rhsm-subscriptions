/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.service.ContractService;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import com.redhat.swatch.contract.test.resources.WireMockResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
class AzureContractLifecycleIntegrationTest {

  private static final String AZURE_CUSTOMER_ID = "azure_customer_id_placeholder";
  private static final String AZURE_SUBSCRIPTION_ID = "d351b825-7e4b-4bfb-aad3-28441b34b5f1";
  private static final String AZURE_RESOURCE_ID = "f226c862-dbc3-4f91-b8ed-1eba40dcdc59";
  private static final String AZURE_CLIENT_ID = "123456789A";
  private static final String RH_SUBSCRIPTION_NUMBER = "42";
  private static final String RH_ORG_ID = "org123";
  private static final String AZURE_UMB_MESSAGE_CONTRACT_CREATED =
      String.format(
          """
          {
            "action":"contract-updated",
            "currentDimensions":[{"dimensionName":"vcpu_hours","dimensionValue":"0"}],
            "cloudIdentifiers":{
              "type":"saas",
              "azureResourceId":"%s",
              "azureOfferId":"azureOfferId",
              "planId":"vcpu-hours",
              "partner":"azure_marketplace"
            }
          }
          """,
          AZURE_RESOURCE_ID);

  private static final String AZURE_PARTNER_API_RESPONSE_CONTRACT_CREATED =
      String.format(
          """
          {
            "content": [
              {
                "entitlementDates": {
                  "endDate": "2030-01-01T00:00:00.000000Z",
                  "startDate": "2024-01-01T00:00:00.000000Z"
                },
                "partnerIdentities": {
                  "azureCustomerId": "%s",
                  "clientId": "%s"
                },
                "purchase": {
                  "azureResourceId": "%s",
                  "contracts": [
                    {
                      "endDate": "2030-01-01T00:00:00.000000Z",
                      "planId": "vcpu-hours",
                      "startDate": "2024-01-01T00:00:00.000000Z"
                    }
                  ],
                  "vendorProductCode": "azureOfferId"
                },
                "rhEntitlements": [
                  {
                    "subscriptionNumber": "%s"
                  }
                ],
                "sourcePartner": "azure_marketplace",
                "status": "SUBSCRIBED"
              }
            ],
            "page": {
              "size": 0,
              "totalElements": 0,
              "totalPages": 0,
              "number": 0
            }
          }
          """,
          AZURE_CUSTOMER_ID, AZURE_CLIENT_ID, AZURE_RESOURCE_ID, RH_SUBSCRIPTION_NUMBER);

  private static final String AZURE_UMB_MESSAGE_ORG_ASSOCIATED =
      String.format(
          """
          {
            "action":"redhat-subscription-created",
            "currentDimensions":[{"dimensionName":"vcpu_hours","dimensionValue":"0"}],
            "cloudIdentifiers":{
              "type":"saas",
              "azureResourceId":"%s",
              "azureOfferId":"azureOfferId",
              "planId":"vcpu-hours",
              "partner":"azure_marketplace"
            },
            "redHatSubscriptionNumber":"%s"
          }
          """,
          AZURE_RESOURCE_ID, RH_SUBSCRIPTION_NUMBER);

  private static final String AZURE_PARTNER_API_RESPONSE_SKU_MISSING =
      String.format(
          """
          {
            "content": [
              {
                "entitlementDates": {
                  "endDate": "2030-01-01T00:00:00.000000Z",
                  "startDate": "2024-01-01T00:00:00.000000Z"
                },
                "partnerIdentities": {
                  "azureCustomerId": "%s",
                  "clientId": "%s"
                },
                "purchase": {
                  "azureResourceId": "%s",
                  "contracts": [
                    {
                      "endDate": "2030-01-01T00:00:00.000000Z",
                      "planId": "vcpu-hours",
                      "startDate": "2024-01-01T00:00:00.000000Z"
                    }
                  ],
                  "vendorProductCode": "azureOfferId"
                },
                "rhAccountId": "%s",
                "rhEntitlements": [
                  {
                    "subscriptionNumber": "%s"
                  }
                ],
                "sourcePartner": "azure_marketplace",
                "status": "SUBSCRIBED"
              }
            ],
            "page": {
              "size": 0,
              "totalElements": 0,
              "totalPages": 0,
              "number": 0
            }
          }
          """,
          AZURE_CUSTOMER_ID, AZURE_CLIENT_ID, AZURE_RESOURCE_ID, RH_ORG_ID, RH_SUBSCRIPTION_NUMBER);

  private static final String AZURE_PARTNER_API_RESPONSE_ORG_ASSOCIATED =
      String.format(
          """
          {
            "content": [
              {
                "entitlementDates": {
                  "endDate": "2030-01-01T00:00:00.000000Z",
                  "startDate": "2024-01-01T00:00:00.000000Z"
                },
                "partnerIdentities": {
                  "azureSubscriptionId": "%s",
                  "azureCustomerId": "%s",
                  "clientId": "%s"
                },
                "purchase": {
                  "azureResourceId": "%s",
                  "contracts": [
                    {
                      "endDate": "2030-01-01T00:00:00.000000Z",
                      "planId": "vcpu-hours",
                      "startDate": "2024-01-01T00:00:00.000000Z"
                    }
                  ],
                  "vendorProductCode": "azureOfferId"
                },
                "rhAccountId": "%s",
                "rhEntitlements": [
                  {
                    "sku": "BASILISK",
                    "subscriptionNumber": "%s"
                  }
                ],
                "sourcePartner": "azure_marketplace",
                "status": "SUBSCRIBED"
              }
            ],
            "page": {
              "size": 0,
              "totalElements": 0,
              "totalPages": 0,
              "number": 0
            }
          }
          """,
          AZURE_SUBSCRIPTION_ID,
          AZURE_CUSTOMER_ID,
          AZURE_CLIENT_ID,
          AZURE_RESOURCE_ID,
          RH_ORG_ID,
          RH_SUBSCRIPTION_NUMBER);

  private static final String AZURE_UMB_MESSAGE_AZURE_SUBSCRIPTION_ID_ADDED =
      String.format(
          """
          {
            "action":"contract-updated",
            "currentDimensions":[{"dimensionName":"vcpu_hours","dimensionValue":"0"}],
            "cloudIdentifiers":{
              "type":"saas",
              "azureResourceId":"%s",
              "azureSubscriptionId":"%s",
              "azureOfferId":"azureOfferId",
              "planId":"vcpu-hours",
              "partner":"azure_marketplace"
            }
          }
          """,
          AZURE_RESOURCE_ID, AZURE_SUBSCRIPTION_ID);

  private static final String AZURE_PARTNER_API_RESPONSE_AZURE_SUBSCRIPTION_ID_ADDED =
      String.format(
          """
          {
            "content": [
              {
                "entitlementDates": {
                  "endDate": "2030-01-01T00:00:00.000000Z",
                  "startDate": "2024-01-01T00:00:00.000000Z"
                },
                "partnerIdentities": {
                  "azureSubscriptionId": "%s",
                  "azureCustomerId": "%s",
                  "clientId": "%s"
                },
                "purchase": {
                  "azureResourceId": "%s",
                  "contracts": [
                    {
                      "endDate": "2030-01-01T00:00:00.000000Z",
                      "planId": "vcpu-hours",
                      "startDate": "2024-01-01T00:00:00.000000Z"
                    }
                  ],
                  "vendorProductCode": "azureOfferId"
                },
                "rhAccountId": "%s",
                "rhEntitlements": [
                  {
                    "sku": "BASILISK",
                    "subscriptionNumber": "%s"
                  }
                ],
                "sourcePartner": "azure_marketplace",
                "status": "SUBSCRIBED"
              }
            ],
            "page": {
              "size": 0,
              "totalElements": 0,
              "totalPages": 0,
              "number": 0
            }
          }
          """,
          AZURE_SUBSCRIPTION_ID,
          AZURE_CUSTOMER_ID,
          AZURE_CLIENT_ID,
          AZURE_RESOURCE_ID,
          RH_ORG_ID,
          RH_SUBSCRIPTION_NUMBER);

  @Inject ContractService contractService;
  @Inject ObjectMapper objectMapper;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @InjectWireMock WireMockServer wireMockServer;

  @BeforeEach
  @Transactional
  public void setup() {
    OfferingEntity offering = new OfferingEntity();
    offering.setSku("BASILISK");
    offeringRepository.persist(offering);
  }

  @AfterEach
  @Transactional
  public void cleanup() {
    subscriptionRepository.deleteAll();
    contractRepository.deleteAll();
    offeringRepository.deleteAll();
  }

  @Test
  void azureContractLifecycleHandledAppropriately() throws JsonProcessingException {
    stubPartnerSubscriptionApi(AZURE_PARTNER_API_RESPONSE_CONTRACT_CREATED);
    var status =
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(
                objectMapper.readValue(
                    AZURE_UMB_MESSAGE_CONTRACT_CREATED, PartnerEntitlementContract.class)));
    assertEquals("FAILED", status.getStatus());
    assertEquals("Contract missing RH orgId", status.getMessage());
    assertEquals(0, contractRepository.count());
    assertEquals(0, subscriptionRepository.count());
    // verifies that no retries are made when org ID is missing from the contract
    wireMockServer.verify(
        exactly(1), postRequestedFor(urlEqualTo("/mock/partnerApi/v1/partnerSubscriptions")));

    stubPartnerSubscriptionApi(AZURE_PARTNER_API_RESPONSE_SKU_MISSING);
    status =
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(
                objectMapper.readValue(
                    AZURE_UMB_MESSAGE_ORG_ASSOCIATED, PartnerEntitlementContract.class)));
    assertEquals("FAILED", status.getStatus());
    assertEquals("Empty value in non-null fields", status.getMessage());
    assertEquals(0, contractRepository.count());
    assertEquals(0, subscriptionRepository.count());
    // verifies that 10 retries are made when SKU is missing from the contract (plus 2 original
    // requests)
    wireMockServer.verify(
        exactly(12), postRequestedFor(urlEqualTo("/mock/partnerApi/v1/partnerSubscriptions")));

    stubPartnerSubscriptionApi(AZURE_PARTNER_API_RESPONSE_ORG_ASSOCIATED);
    status =
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(
                objectMapper.readValue(
                    AZURE_UMB_MESSAGE_ORG_ASSOCIATED, PartnerEntitlementContract.class)));
    assertEquals("SUCCESS", status.getStatus());
    assertEquals(1, contractRepository.count());
    assertEquals(1, subscriptionRepository.count());

    stubPartnerSubscriptionApi(AZURE_PARTNER_API_RESPONSE_AZURE_SUBSCRIPTION_ID_ADDED);
    status =
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(
                objectMapper.readValue(
                    AZURE_UMB_MESSAGE_AZURE_SUBSCRIPTION_ID_ADDED,
                    PartnerEntitlementContract.class)));
    assertEquals("SUCCESS", status.getStatus());
    assertEquals(1, contractRepository.count());
    assertEquals(1, subscriptionRepository.count());
    var contract = contractRepository.findAll().stream().findFirst().orElseThrow();
    var subscription = subscriptionRepository.findAll().stream().findFirst().orElseThrow();
    assertEquals(AZURE_SUBSCRIPTION_ID, contract.getBillingAccountId());
    assertEquals(AZURE_SUBSCRIPTION_ID, subscription.getBillingAccountId());
    String expectedBillingProviderId =
        String.format(
            "%s;%s;%s;%s;%s",
            AZURE_RESOURCE_ID, "vcpu-hours", "azureOfferId", AZURE_CUSTOMER_ID, AZURE_CLIENT_ID);
    assertEquals(expectedBillingProviderId, contract.getBillingProviderId());
    assertEquals(expectedBillingProviderId, subscription.getBillingProviderId());
  }

  private void stubPartnerSubscriptionApi(String jsonBody) {
    wireMockServer.stubFor(
        any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(jsonBody)));
  }
}
