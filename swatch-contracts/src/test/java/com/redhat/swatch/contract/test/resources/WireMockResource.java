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
package com.redhat.swatch.contract.test.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.swatch.contract.PathUtils;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WireMockResource implements QuarkusTestResourceLifecycleManager {
  public static final String DEFAULT_START_DATE = "2022-09-23T20:07:51.010445001Z";
  public static final String DEFAULT_END_DATE = "2023-04-20T00:09:14.192515001Z";
  private static final String BASE_KEYSTORE_PATH =
      Paths.get(PathUtils.PROJECT_DIRECTORY, "../clients-core/src/test/resources").toString();
  private static final String SERVER_KEYSTORE_PATH =
      String.format("%s/server.jks", BASE_KEYSTORE_PATH);
  private static final String CLIENT_KEYSTORE_PATH =
      String.format("%s/client.jks", BASE_KEYSTORE_PATH);
  public static final String CLIENT_KEYSTORE_RESOURCE =
      String.format("file:%s", CLIENT_KEYSTORE_PATH);
  private static final String TRUSTSTORE_PATH = String.format("%s/test-ca.jks", BASE_KEYSTORE_PATH);
  public static final String STORE_PASSWORD = "password";
  private WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.options()
                .httpDisabled(true)
                .dynamicHttpsPort()
                .keystorePath(SERVER_KEYSTORE_PATH)
                .keystorePassword(STORE_PASSWORD)
                .needClientAuth(true)
                .trustStorePath(TRUSTSTORE_PATH)
                .trustStorePassword(STORE_PASSWORD)
                .notifier(new ConsoleNotifier(true)));
    setup(wireMockServer);
    wireMockServer.start();
    var config = new HashMap<String, String>();
    config.put("KEYSTORE_PATH", CLIENT_KEYSTORE_PATH);
    config.put("KEYSTORE_RESOURCE", CLIENT_KEYSTORE_RESOURCE);
    config.put("KEYSTORE_PASSWORD", STORE_PASSWORD);
    config.put("TRUSTSTORE_PATH", TRUSTSTORE_PATH);
    config.put("TRUSTSTORE_RESOURCE", String.format("file:%s", TRUSTSTORE_PATH));
    config.put("TRUSTSTORE_PASSWORD", STORE_PASSWORD);
    config.put("SUBSCRIPTION_KEYSTORE_RESOURCE", CLIENT_KEYSTORE_RESOURCE);
    config.put("SUBSCRIPTION_KEYSTORE_PASSWORD", STORE_PASSWORD);
    config.put("PRODUCT_KEYSTORE_RESOURCE", CLIENT_KEYSTORE_RESOURCE);
    config.put("PRODUCT_KEYSTORE_PASSWORD", STORE_PASSWORD);
    config.put(
        "ENTITLEMENT_GATEWAY_URL", String.format("%s/mock/partnerApi", wireMockServer.baseUrl()));
    config.put("SUBSCRIPTION_URL", String.format("%s/mock/subscription", wireMockServer.baseUrl()));
    return config;
  }

  public static void setup(WireMockServer wireMockServer) {
    wireMockServer.resetAll();
    stubForRhPartnerApi(wireMockServer);
    stubForSubscriptionService(wireMockServer);
  }

  private static void stubForSubscriptionService(WireMockServer wireMockServer) {
    wireMockServer.stubFor(
        any(urlMatching("/mock/subscription/search.*subscription_number.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                                            [
                                              {
                                                "id": "123456"
                                              }
                                            ]
                                            """)));
  }

  private static void stubForRhPartnerApi(WireMockServer wireMockServer) {
    wireMockServer.stubFor(
        any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                                                    {
                                                      "content": [
                                                        {
                                                          "rhAccountId": "org123",
                                                          "sourcePartner": "aws_marketplace",
                                                          "partnerIdentities": {
                                                            "awsCustomerId": "HSwCpt6sqkC",
                                                            "customerAwsAccountId": "568056954830",
                                                            "sellerAccountId": "568056954830"
                                                          },
                                                          "rhEntitlements": [
                                                            {
                                                              "sku": "RH000000",
                                                              "subscriptionNumber": "123456"
                                                            }
                                                          ],
                                                          "purchase": {
                                                            "vendorProductCode": "1234567890abcdefghijklmno",
                                                            "contracts": [
                                                              {
                                                                "startDate": "%s",
                                                                "endDate": "%s",
                                                                "dimensions": [
                                                                  {
                                                                    "name": "control_plane",
                                                                    "value": "1000000"
                                                                  },
                                                                  {
                                                                    "name": "four_vcpu_hour",
                                                                    "value": "1000000"
                                                                  }
                                                                ]
                                                              },
                                                              {
                                                                "startDate": "2023-04-20T00:09:14.192515Z",
                                                                "dimensions": [
                                                                  {
                                                                    "name": "control_plane",
                                                                    "value": "1000000"
                                                                  },
                                                                  {
                                                                    "name": "four_vcpu_hour",
                                                                    "value": "1000000"
                                                                  }
                                                                ]
                                                              }
                                                            ]
                                                          },
                                                          "status": "STATUS",
                                                          "entitlementDates": {
                                                            "startDate": "2023-03-17T12:29:48.569Z",
                                                            "endDate": "2024-03-17T12:29:48.569Z"
                                                          }
                                                        },
                                                        {
                                                         "rhAccountId": "7186626",
                                                         "sourcePartner": "azure_marketplace",
                                                         "partnerIdentities": {
                                                             "azureSubscriptionId": "fa650050-dedd-4958-b901-d8e5118c0a5f",
                                                             "azureCustomerId": "eadf26ee-6fbc-4295-9a9e-25d4fea8951d_2019-05-31"
                                                         },
                                                         "rhEntitlements": [
                                                             {
                                                                 "sku": "RH000000",
                                                                 "subscriptionNumber": "13294886"
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
                                                             "endDate": "2024-06-09T19:37:46.651363Z"
                                                         }
                                                     }
                                                    ],
                                                      "page": {
                                                        "size": 0,
                                                        "totalElements": 0,
                                                        "totalPages": 0,
                                                        "number": 0
                                                      }
                                                    }

                                                    """
                            .formatted(DEFAULT_START_DATE, DEFAULT_END_DATE))));
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(
        wireMockServer,
        new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
  }
}
