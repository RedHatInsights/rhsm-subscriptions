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
package com.redhat.swatch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import java.util.Map;

public class WireMockResource
    implements QuarkusTestResourceConfigurableLifecycleManager<WireMockTest> {

  private static final String BASE_KEYSTORE_PATH = "../clients-core/src/test/resources";
  private static final String SERVER_KEYSTORE_PATH =
      String.format("%s/server.jks", BASE_KEYSTORE_PATH);
  private static final String CLIENT_KEYSTORE_PATH =
      String.format("%s/client.jks", BASE_KEYSTORE_PATH);
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
    stubForRhPartnerApi(wireMockServer);
    wireMockServer.start();
    return Map.of(
        "KEYSTORE_RESOURCE", String.format("file:%s", CLIENT_KEYSTORE_PATH),
        "KEYSTORE_PASSWORD", STORE_PASSWORD,
        "TRUSTSTORE_RESOURCE", String.format("file:%s", TRUSTSTORE_PATH),
        "TRUSTSTORE_PASSWORD", STORE_PASSWORD,
        "ENTITLEMENT_GATEWAY_URL", String.format("%s/mock/partnerApi", wireMockServer.baseUrl()));
  }

  private void stubForRhPartnerApi(WireMockServer wireMockServer) {
    wireMockServer.stubFor(
        any(urlMatching("/mock/partnerApi/v1/partnerSubscriptions"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "partnerEntitlements": [
                            {
                              "rhAccountId": "org123",
                              "sourcePartner": "aws_marketplace",
                              "purchase": {
                                "productCode": "1234567890abcdefghijklmno",
                                "sku": "RH000000",
                                "subscriptionNumber": "123456",
                                "contracts": [
                                {
                                  "startDate": "2022-09-23T20:07:51.010445Z",
                                  "endDate": "2022-10-23T20:07:51.01054Z",
                                  "dimensions": [
                                    {
                                      "name": "foobar",
                                      "value": 1000000
                                    }
                                  ]
                                }
                                ]
                              },
                              "status": "SUBSCRIBED",
                              "extra": "this shows we ignore unknown fields"
                            }
                          ],
                          "hasNext": true
                        }
                        """)));
  }

  @Override
  public void stop() {
    wireMockServer.stop();
  }
}
