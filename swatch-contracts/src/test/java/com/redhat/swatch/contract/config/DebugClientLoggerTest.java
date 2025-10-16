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
package com.redhat.swatch.contract.config;

import static com.redhat.swatch.resteasy.client.DebugClientLogger.LOG_RESPONSES_PROPERTY;
import static com.redhat.swatch.resteasy.client.DebugClientLogger.URI_FILTER_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import com.redhat.swatch.contract.test.resources.LoggerCaptor;
import com.redhat.swatch.contract.test.resources.WireMockResource;
import com.redhat.swatch.resteasy.client.DebugClientLogger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DebugClientLoggerTest.LogAll.class)
@QuarkusTestResource(value = WireMockResource.class, restrictToAnnotatedClass = true)
public class DebugClientLoggerTest {
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();
  @RestClient SearchApi searchApi;
  @RestClient PartnerApi partnerApi;
  @InjectWireMock WireMockServer wireMockServer;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(DebugClientLogger.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @BeforeEach
  void setUp() {
    LOGGER_CAPTOR.clearRecords();
  }

  @Test
  void testRequestLog() throws ApiException {
    searchApi.getSubscriptionBySubscriptionNumber("any");
    thenLogWithMessage(
        "Request method=GET URI=https://localhost:%s/mock/subscription/search/criteria;subscription_number=any"
            .formatted(wireMockServer.httpsPort()));
  }

  @Test
  void testResponseLog() throws ApiException {
    List<Subscription> response = searchApi.getSubscriptionBySubscriptionNumber("any");
    assertNotNull(response);
    assertEquals(1, response.size());
    assertEquals(123456, response.get(0).getId());
    thenLogWithMessage(
        "Response method=GET URI=https://localhost:%s/mock/subscription/search/criteria;subscription_number=any"
            .formatted(wireMockServer.httpsPort()));
  }

  @Test
  void testNoLogsIfNotConfigured()
      throws com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException {
    partnerApi.getPartnerEntitlements(null);
    thenLogNothing();
  }

  private void thenLogNothing() {
    assertTrue(LOGGER_CAPTOR.getRecords().isEmpty());
  }

  private void thenLogWithMessage(String str) {
    LOGGER_CAPTOR.thenDebugLogWithMessage(str);
  }

  public static class LogAll implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      // Only debug the subscription mock rest client:
      return Map.of(URI_FILTER_PROPERTY, ".*mock/subscription.*", LOG_RESPONSES_PROPERTY, "true");
    }
  }
}
