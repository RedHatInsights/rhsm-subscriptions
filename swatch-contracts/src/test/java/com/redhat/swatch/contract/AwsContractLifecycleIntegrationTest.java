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
class AwsContractLifecycleIntegrationTest {

  @Inject ContractService contractService;
  @Inject ObjectMapper objectMapper;
  @Inject SubscriptionRepository subscriptionRepository;
  @Inject ContractRepository contractRepository;
  @Inject OfferingRepository offeringRepository;
  @InjectWireMock WireMockServer wireMockServer;

  static final String AWS_ACCOUNT_ID = "256af912-d792-4dd4-811d-1152375f1f41";
  static final String AWS_CUSTOMER_ID = "aws_customer_id_placeholder";
  static final String AWS_SELLER_ID = "f226c862-dbc3-4f91-b8ed-1eba40dcdc59";
  static final String RH_SUBSCRIPTION_NUMBER = "42";
  static final String RH_ORG_ID = "org123";
  static final String UMB_SUBSCRIPTION_START_DATE = "2023-03-17T12:29:48.569Z";
  static final String AWS_PRODUCT_CODE = "testProductCode";

  static String AWS_UMB_MESSAGE_CONTRACT_CREATED =
      String.format(
          """
          {
            "action": "redhat-subscription-created",
            "redHatSubscriptionNumber": "%s",
            "cloudIdentifiers": {
              "seller": {
                "id": "%s"
              },
              "awsCustomerId": "%s",
              "awsCustomerAccountId": "%s",
              "productCode": "testProductCode",
              "partner": "aws_marketplace"
            }
          }
          """,
          RH_SUBSCRIPTION_NUMBER, AWS_SELLER_ID, AWS_CUSTOMER_ID, AWS_ACCOUNT_ID);

  static String AWS_PARTNER_API_RESPONSE_CONTRACT_CREATED =
      String.format(
          """
          {
              "content": [
                  {
                      "sourcePartner": "aws_marketplace",
                      "partnerIdentities": {
                          "awsCustomerId": "%s",
                          "customerAwsAccountId": "%s",
                          "sellerAccountId": "%s"
                      },
                      "rhAccountId": "%s",
                      "rhEntitlements": [
                          {
                              "sku": "BASILISK",
                              "subscriptionNumber": "%s"
                          }
                      ],
                      "purchase": {
                          "vendorProductCode": "%s",
                          "contracts": [
                              {
                                  "startDate": "%s",
                                  "dimensions": [
                                      {
                                          "name": "four_vcpu_hour",
                                          "value": "730"
                                      }
                                  ]
                              }
                          ]
                      },
                      "status": "STATUS",
                      "entitlementDates": {
                          "startDate": "%s"
                      }
                  }
              ],
              "page": {
                  "size": 0,
                  "totalElements": 1,
                  "totalPages": 0,
                  "number": 0
              }
          }
          """,
          AWS_CUSTOMER_ID,
          AWS_ACCOUNT_ID,
          AWS_SELLER_ID,
          RH_ORG_ID,
          RH_SUBSCRIPTION_NUMBER,
          AWS_PRODUCT_CODE,
          UMB_SUBSCRIPTION_START_DATE,
          UMB_SUBSCRIPTION_START_DATE);

  @BeforeEach
  @Transactional
  public void setup() {
    OfferingEntity offering = new OfferingEntity();
    offering.setSku("BASILISK");
    offering.getProductTags().add("BASILISK");
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
  void testAwsContractLifecycleHandledAppropriately() throws JsonProcessingException {
    stubPartnerSubscriptionApi(AWS_PARTNER_API_RESPONSE_CONTRACT_CREATED);
    var status =
        contractService.createPartnerContract(
            PartnerEntitlementsRequest.from(
                objectMapper.readValue(
                    AWS_UMB_MESSAGE_CONTRACT_CREATED, PartnerEntitlementContract.class)));
    assertEquals("SUCCESS", status.getStatus());
    assertEquals("New contract created", status.getMessage());
    assertEquals(1, contractRepository.count());
    assertEquals(1, subscriptionRepository.count());
  }

  private void stubPartnerSubscriptionApi(String jsonBody) {
    wireMockServer.stubFor(
        any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(jsonBody)));
  }
}
