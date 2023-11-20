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
package com.redhat.swatch.metrics.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.fail;
import static wiremock.com.google.common.net.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.swatch.clients.prometheus.api.model.QueryResult;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class PrometheusQueryWiremock {

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

  private final WireMockServer wireMockServer;

  protected PrometheusQueryWiremock(WireMockServer wireMockServer) {
    this.wireMockServer = wireMockServer;
    // We're missing the `@JsonValue` annotation in enumerators, so serialization will fail
    // This is not a prod issue, because we never serialize these POJOs, so this is a workaround
    // which is intended only for tests:
    SimpleModule module = new SimpleModule();
    module.addSerializer(StatusType.class, new ToStringEnumSerializer());
    module.addSerializer(ResultType.class, new ToStringEnumSerializer());
    this.objectMapper.registerModule(module);
  }

  public void stubQuery(
      String expectedQuery,
      int expectedTimeout,
      OffsetDateTime expectedTime,
      QueryResult expectedResult) {
    wireMockServer.stubFor(
        get(urlPathEqualTo(QUERY_PATH))
            .withQueryParam(QUERY_PARAM, equalTo(expectedQuery))
            .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(expectedTimeout)))
            // the client uses a specific formatter; see
            // org.candlepin.subscriptions.prometheus.JavaTimeFormatter
            .withQueryParam(
                TIME_PARAM, equalTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expectedTime)))
            .willReturn(okJson(toJson(expectedResult))));
  }

  public void stubQueryRange(
      String expectedQuery,
      OffsetDateTime expectedStart,
      OffsetDateTime expectedEnd,
      int expectedStep,
      int expectedTimeout,
      QueryResult expectedResult) {
    wireMockServer.stubFor(
        get(urlPathEqualTo(QUERY_RANGE_PATH))
            .withQueryParam(QUERY_PARAM, equalTo(expectedQuery))
            .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(expectedTimeout)))
            .withQueryParam(START_PARAM, equalTo(Long.toString(expectedStart.toEpochSecond())))
            .withQueryParam(END_PARAM, equalTo(Long.toString(expectedEnd.toEpochSecond())))
            .withQueryParam(STEP_PARAM, equalTo(String.valueOf(expectedStep)))
            .willReturn(okJson(toJson(expectedResult))));
  }

  public void stubQueryRange(QueryResult... expectedResults) {
    String scenarioId = UUID.randomUUID().toString();
    int stepId = 0;
    String currentState = STARTED;
    for (QueryResult result : expectedResults) {
      String nextState = STEP_PREFIX + ++stepId;
      wireMockServer.stubFor(
          get(urlPathEqualTo(QUERY_RANGE_PATH))
              .inScenario(scenarioId)
              .whenScenarioStateIs(currentState)
              .willReturn(okJson(toJson(result)))
              .willSetStateTo(nextState));
      currentState = nextState;
    }
  }

  public void stubQueryRangeWithFile(String file) {
    wireMockServer.stubFor(
        get(urlPathEqualTo(QUERY_RANGE_PATH))
            .willReturn(ok().withHeader(CONTENT_TYPE, APPLICATION_JSON).withBodyFile(file)));
  }

  public void verifyQueryRangeWasCalled(int times) {
    wireMockServer.verify(exactly(times), getRequestedFor(urlPathEqualTo(QUERY_RANGE_PATH)));
  }

  public void verifyQueryRange(
      String query, OffsetDateTime start, OffsetDateTime end, int step, int queryTimeout) {
    wireMockServer.verify(
        getRequestedFor(urlPathEqualTo(QUERY_RANGE_PATH))
            .withQueryParam(QUERY_PARAM, equalTo(query))
            .withQueryParam(TIMEOUT_PARAM, equalTo(String.valueOf(queryTimeout)))
            .withQueryParam(START_PARAM, equalTo(Long.toString(start.toEpochSecond())))
            .withQueryParam(END_PARAM, equalTo(Long.toString(end.toEpochSecond())))
            .withQueryParam(STEP_PARAM, equalTo(String.valueOf(step))));
  }

  public void resetScenario() {
    wireMockServer.resetScenarios();
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      fail("Fail to serialize the query result object", e);
      return null;
    }
  }

  private static final class ToStringEnumSerializer extends JsonSerializer<Enum> {

    @Override
    public void serialize(Enum value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.toString());
    }
  }
}
