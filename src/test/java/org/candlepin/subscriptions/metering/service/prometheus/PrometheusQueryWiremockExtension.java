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
package org.candlepin.subscriptions.metering.service.prometheus;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.fail;
import static wiremock.com.google.common.net.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.junit.jupiter.api.extension.*;

/**
 * Utility class to initialize and stub a mocked version of the prometheus server using WireMock.
 *
 * <p>The test classes need to configure the prometheus url to use `localhost:9000`. Example:
 * {@code @SpringBootTest(properties =
 * "rhsm-subscriptions.metering.prometheus.client.url=http://localhost:${WIREMOCK_PORT:8101}")}
 */
public class PrometheusQueryWiremockExtension
    implements BeforeAllCallback, AfterEachCallback, BeforeEachCallback, ParameterResolver {
  private static final String PROMETHEUS_DEFAULT_PORT = "8101";
  private static final String WIREMOCK_PORT = "WIREMOCK_PORT";
  private static final String QUERY_PATH = "/query";
  private static final String QUERY_RANGE_PATH = "/query_range";
  private static final String QUERY_PARAM = "query";
  private static final String TIMEOUT_PARAM = "timeout";
  private static final String START_PARAM = "start";
  private static final String END_PARAM = "end";
  private static final String STEP_PARAM = "step";
  private static final String TIME_PARAM = "time";
  private static final String STEP_PREFIX = "STEP_";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private WireMockServer prometheusServer;

  @Override
  public void beforeAll(ExtensionContext context) {
    int wiremockPort =
        Integer.parseInt(
            Optional.ofNullable(System.getenv(WIREMOCK_PORT)).orElse(PROMETHEUS_DEFAULT_PORT));
    prometheusServer = new WireMockServer(wiremockPort);
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    prometheusServer.resetAll();
    prometheusServer.start();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    prometheusServer.stop();
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return PrometheusQueryWiremock.class.equals(parameterContext.getParameter().getType());
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return new PrometheusQueryWiremock(this);
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      fail("Fail to serialize the query result object", e);
      return null;
    }
  }

  public static class PrometheusQueryWiremock {

    private final PrometheusQueryWiremockExtension extension;

    private PrometheusQueryWiremock(PrometheusQueryWiremockExtension extension) {
      this.extension = extension;
    }

    public void stubQuery(
        String expectedQuery,
        int expectedTimeout,
        OffsetDateTime expectedTime,
        QueryResult expectedResult) {
      extension.prometheusServer.stubFor(
          get(urlPathEqualTo(QUERY_PATH))
              .withQueryParam(QUERY_PARAM, equalTo(expectedQuery))
              .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(expectedTimeout)))
              // the client uses a specific formatter; see
              // org.candlepin.subscriptions.prometheus.JavaTimeFormatter
              .withQueryParam(
                  TIME_PARAM, equalTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expectedTime)))
              .willReturn(okJson(extension.toJson(expectedResult))));
    }

    public void stubQueryRange(
        String expectedQuery,
        OffsetDateTime expectedStart,
        OffsetDateTime expectedEnd,
        int expectedStep,
        int expectedTimeout,
        QueryResult expectedResult) {
      extension.prometheusServer.stubFor(
          get(urlPathEqualTo(QUERY_RANGE_PATH))
              .withQueryParam(QUERY_PARAM, equalTo(expectedQuery))
              .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(expectedTimeout)))
              .withQueryParam(START_PARAM, equalTo(Long.toString(expectedStart.toEpochSecond())))
              .withQueryParam(END_PARAM, equalTo(Long.toString(expectedEnd.toEpochSecond())))
              .withQueryParam(STEP_PARAM, equalTo(String.valueOf(expectedStep)))
              .willReturn(okJson(extension.toJson(expectedResult))));
    }

    public void stubQueryRange(QueryResult... expectedResults) {
      String scenarioId = UUID.randomUUID().toString();
      int stepId = 0;
      String currentState = STARTED;
      for (QueryResult result : expectedResults) {
        String nextState = STEP_PREFIX + ++stepId;
        extension.prometheusServer.stubFor(
            get(urlPathEqualTo(QUERY_RANGE_PATH))
                .inScenario(scenarioId)
                .whenScenarioStateIs(currentState)
                .willReturn(okJson(extension.toJson(result)))
                .willSetStateTo(nextState));
        currentState = nextState;
      }
    }

    public void stubQueryRangeWithFile(String file) {
      extension.prometheusServer.stubFor(
          get(urlPathEqualTo(QUERY_RANGE_PATH))
              .willReturn(ok().withHeader(CONTENT_TYPE, APPLICATION_JSON).withBodyFile(file)));
    }

    public void verifyQueryRangeWasCalled(int times) {
      extension.prometheusServer.verify(
          exactly(times), getRequestedFor(urlPathEqualTo(QUERY_RANGE_PATH)));
    }

    public void verifyQueryRange(
        String query, OffsetDateTime start, OffsetDateTime end, int step, int queryTimeout) {
      extension.prometheusServer.verify(
          getRequestedFor(urlPathEqualTo(QUERY_RANGE_PATH))
              .withQueryParam(QUERY_PARAM, equalTo(query))
              .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(queryTimeout)))
              .withQueryParam(START_PARAM, equalTo(Long.toString(start.toEpochSecond())))
              .withQueryParam(END_PARAM, equalTo(Long.toString(end.toEpochSecond())))
              .withQueryParam(STEP_PARAM, equalTo(String.valueOf(step))));
    }

    public void resetScenario() {
      extension.prometheusServer.resetScenario(STARTED);
    }
  }
}
