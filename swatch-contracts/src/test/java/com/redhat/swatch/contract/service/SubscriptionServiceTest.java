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
package com.redhat.swatch.contract.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.swatch.contract.exception.ExternalServiceException;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import com.redhat.swatch.contract.test.resources.WireMockResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SubscriptionServiceTest.ConfigureSearchApiRetry.class)
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
class SubscriptionServiceTest {

  @ConfigProperty(name = "SUBSCRIPTION_MAX_RETRY_ATTEMPTS", defaultValue = "4")
  int maxRetries;

  @InjectWireMock WireMockServer wireMockServer;

  @Inject SubscriptionService subject;

  @BeforeEach
  void setup() {
    wireMockServer.resetAll();
  }

  @Test
  void verifySearchByOrgIdTest() {
    givenSearchApiReturnsOk();
    subject.getSubscriptionsByOrgId("123", 0, 1);
    wireMockServer.verify(
        getRequestedFor(urlMatching("/mock/subscription/search.*web_customer_id.*")));
  }

  @Test
  void verifySearchByOrgIdRetriesOnExceptions() {
    givenSearchApiReturnsError();
    assertThrows(
        ExternalServiceException.class, () -> subject.getSubscriptionsByOrgId("123", 0, 1));
    // expected the configured maxRetries plus the first call.
    wireMockServer.verify(
        maxRetries + 1,
        getRequestedFor(urlMatching("/mock/subscription/search.*web_customer_id.*")));
  }

  @Test
  void verifyGetByIdTest() {
    givenSearchApiReturnsOk();
    subject.getSubscriptionById("123");
    wireMockServer.verify(getRequestedFor(urlMatching("/mock/subscription/id.*")));
  }

  private void givenSearchApiReturnsError() {
    wireMockServer.stubFor(
        any(urlMatching("/mock/subscription/search.*web_customer_id.*"))
            .willReturn(aResponse().withStatus(500)));
  }

  private void givenSearchApiReturnsOk() {
    wireMockServer.stubFor(
        any(urlMatching("/mock/subscription/search.*web_customer_id.*"))
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
    wireMockServer.stubFor(
        any(urlMatching("/mock/subscription/id.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                                                                {
                                                                    "id": "123456"
                                                                }
                                                                """)));
  }

  public static class ConfigureSearchApiRetry implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      Map<String, String> config = new HashMap<>();
      config.put("SUBSCRIPTION_MAX_RETRY_ATTEMPTS", "2");
      config.put("SUBSCRIPTION_BACK_OFF_INITIAL_INTERVAL", "200ms");
      return config;
    }
  }
}
