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
package com.redhat.swatch.metrics.service.prometheus;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.clients.prometheus.api.resources.ApiException;
import com.redhat.swatch.clients.prometheus.api.resources.QueryApi;
import com.redhat.swatch.clients.prometheus.api.resources.QueryRangeApi;
import com.redhat.swatch.metrics.exception.ErrorCode;
import com.redhat.swatch.metrics.exception.ExternalServiceException;
import com.redhat.swatch.metrics.service.prometheus.model.QuerySummaryResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wraps prometheus specific API calls to make them more application specific. */
@ApplicationScoped
public class PrometheusService {

  private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);
  private static final String JSON_PROPERTY_RESULT_TYPE = "resultType";
  private static final String JSON_PROPERTY_RESULT = "result";
  public static final String JSON_PROPERTY_STATUS = "status";
  public static final String JSON_PROPERTY_DATA = "data";
  public static final String JSON_PROPERTY_ERROR_TYPE = "errorType";
  public static final String JSON_PROPERTY_ERROR = "error";

  private final QueryApi queryApi;
  private final QueryRangeApi queryRangeApi;
  private final JsonFactory factory;

  public PrometheusService(
      @RestClient QueryApi queryApi, @RestClient QueryRangeApi queryRangeApi, JsonFactory factory) {
    this.queryApi = queryApi;
    this.queryRangeApi = queryRangeApi;
    this.factory = factory;
  }

  public QuerySummaryResult runRangeQuery(
      String query,
      OffsetDateTime start,
      OffsetDateTime end,
      Integer step,
      Integer timeout,
      Consumer<QueryResultDataResultInner> resultDataItemConsumer)
      throws ExternalServiceException {
    log.info("Fetching metrics from prometheus: {} -> {} [Step: {}]", start, end, step);
    try {
      log.debug(
          "Running prometheus range query: Start: {} End: {} Step: {}, Query: {}",
          start.toEpochSecond(),
          end.toEpochSecond(),
          step,
          query);
      File data =
          queryRangeApi.queryRange(
              query, start.toEpochSecond(), end.toEpochSecond(), Integer.toString(step), timeout);
      return parseQueryResult(data, resultDataItemConsumer);
    } catch (ProcessingException | ApiException ex) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR, formatErrorMessage(ex), ex);
    }
  }

  public QuerySummaryResult runQuery(
      String query,
      OffsetDateTime time,
      Integer timeout,
      Consumer<QueryResultDataResultInner> itemConsumer)
      throws ExternalServiceException {
    log.debug("Fetching metrics from prometheus: {}", time);
    try {
      log.debug("Running prometheus query: Time: {}, Query: {}", time.toEpochSecond(), query);
      File data = queryApi.query(query, time, timeout);
      return parseQueryResult(data, itemConsumer);
    } catch (ProcessingException | ApiException ex) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR, formatErrorMessage(ex), ex);
    }
  }

  private String formatErrorMessage(Exception ex) {
    return String.format("Prometheus API Error! MESSAGE: %s", ex.getMessage());
  }

  /**
   * Parse the response. See <a
   * href="https://prometheus.io/docs/prometheus/latest/querying/api/#format-overview">Prometheus
   * HTTP API format overview</a>
   */
  private QuerySummaryResult parseQueryResult(
      File data, Consumer<QueryResultDataResultInner> itemConsumer) {

    var builder = QuerySummaryResult.builder();
    try (JsonParser parser = factory.createParser(data)) {
      while (isNot(parser.nextToken(), JsonToken.END_OBJECT)) {
        if (JSON_PROPERTY_STATUS.equals(parser.getCurrentName())) {
          set(parser, StatusType::fromValue, builder::status);
        } else if (JSON_PROPERTY_ERROR_TYPE.equals(parser.getCurrentName())) {
          set(parser, builder::errorType);
        } else if (JSON_PROPERTY_ERROR.equals(parser.getCurrentName())) {
          set(parser, builder::error);
        } else if (JSON_PROPERTY_DATA.equals(parser.getCurrentName())) {
          parseQueryData(parser, builder, itemConsumer);
        }
      }
    } catch (IOException ex) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR, "Error parsing the Prometheus response", ex);
    }

    return builder.build();
  }

  /**
   * Parse the data property. See "data section of the query result" at <a
   * href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
   */
  private void parseQueryData(
      JsonParser parser,
      QuerySummaryResult.QuerySummaryResultBuilder builder,
      Consumer<QueryResultDataResultInner> itemConsumer)
      throws IOException {
    while (isNot(parser.nextToken(), JsonToken.END_OBJECT)) {
      if (JSON_PROPERTY_RESULT_TYPE.equals(parser.getCurrentName())) {
        set(parser, ResultType::fromValue, builder::resultType);
      } else if (JSON_PROPERTY_RESULT.equals(parser.getCurrentName())) {
        parseDataResultArray(parser, builder, itemConsumer);
      }
    }
  }

  /**
   * Parse the data.result array. See <a
   * href="https://prometheus.io/docs/prometheus/latest/querying/api/#expression-query-result-formats">Prometheus
   * query result formats</a>
   */
  private void parseDataResultArray(
      JsonParser parser,
      QuerySummaryResult.QuerySummaryResultBuilder builder,
      Consumer<QueryResultDataResultInner> itemConsumer)
      throws IOException {
    // consume the [ (START_ARRAY) symbol
    parser.nextToken();
    int numOfResults = 0;
    while (isNot(parser.nextToken(), JsonToken.END_ARRAY)) {
      // NOTE: parser.readValueAs starts with currentToken as START_OBJECT
      itemConsumer.accept(parser.readValueAs(QueryResultDataResultInner.class));
      numOfResults++;
    }

    builder.numOfResults(numOfResults);
  }

  private void set(JsonParser parser, Consumer<String> consumer) throws IOException {
    set(parser, Function.identity(), consumer);
  }

  private <T> void set(JsonParser parser, Function<String, T> transform, Consumer<T> consumer)
      throws IOException {
    String str = parser.nextTextValue();
    if (str != null && !str.isBlank()) {
      consumer.accept(transform.apply(str));
    }
  }

  private boolean isNot(JsonToken token, JsonToken status) {
    return token != JsonToken.VALUE_NULL && token != status;
  }
}
