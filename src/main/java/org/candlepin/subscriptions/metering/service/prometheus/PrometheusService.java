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

import static org.candlepin.subscriptions.prometheus.model.QueryResult.JSON_PROPERTY_DATA;
import static org.candlepin.subscriptions.prometheus.model.QueryResult.JSON_PROPERTY_ERROR;
import static org.candlepin.subscriptions.prometheus.model.QueryResult.JSON_PROPERTY_ERROR_TYPE;
import static org.candlepin.subscriptions.prometheus.model.QueryResult.JSON_PROPERTY_STATUS;
import static org.candlepin.subscriptions.prometheus.model.QueryResultData.JSON_PROPERTY_RESULT;
import static org.candlepin.subscriptions.prometheus.model.QueryResultData.JSON_PROPERTY_RESULT_TYPE;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.UrlEscapers;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.function.Function;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.metering.service.prometheus.model.QuerySummaryResult;
import org.candlepin.subscriptions.prometheus.ApiException;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Wraps prometheus specific API calls to make them more application specific. */
@Component
public class PrometheusService {

  private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);

  private final ApiProvider apiProvider;
  private final JsonFactory factory;

  public PrometheusService(ApiProvider prometheusApiProvider, ObjectMapper objectMapper) {
    this.apiProvider = prometheusApiProvider;
    this.factory = new JsonFactory(objectMapper);
  }

  public QuerySummaryResult runRangeQuery(
      String promQL,
      OffsetDateTime start,
      OffsetDateTime end,
      Integer step,
      Integer timeout,
      Consumer<QueryResultDataResultInner> resultDataItemConsumer)
      throws ExternalServiceException {
    log.info("Fetching metrics from prometheus: {} -> {} [Step: {}]", start, end, step);
    try {
      String query = sanitizeQuery(promQL);
      log.debug(
          "Running prometheus range query: Start: {} End: {} Step: {}, Query: {}",
          start.toEpochSecond(),
          end.toEpochSecond(),
          step,
          query);
      File data =
          apiProvider
              .queryRangeApi()
              .queryRange(
                  query,
                  start.toEpochSecond(),
                  end.toEpochSecond(),
                  Integer.toString(step),
                  timeout);
      return parseQueryResult(data, resultDataItemConsumer);
    } catch (ApiException apie) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR, formatErrorMessage(apie), apie);
    }
  }

  public QuerySummaryResult runQuery(
      String promQL,
      OffsetDateTime time,
      Integer timeout,
      Consumer<QueryResultDataResultInner> itemConsumer)
      throws ExternalServiceException {
    log.debug("Fetching metrics from prometheus: {}", time);
    try {
      String query = sanitizeQuery(promQL);
      log.debug("Running prometheus query: Time: {}, Query: {}", time.toEpochSecond(), query);
      File data = apiProvider.queryApi().query(query, time, timeout);
      return parseQueryResult(data, itemConsumer);
    } catch (ApiException apie) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR, formatErrorMessage(apie), apie);
    }
  }

  private String sanitizeQuery(String promQL) {
    // NOTE: While the ApiClient **should** in theory already encode the query,
    //       it does not handle the curly braces correctly causing issues
    //       when the request is made.
    return UrlEscapers.urlFragmentEscaper().escape(promQL);
  }

  private String formatErrorMessage(ApiException apie) {
    return String.format(
        "Prometheus API Error! CODE: %s MESSAGE: %s", apie.getCode(), apie.getMessage());
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
    if (StringUtils.hasText(str)) {
      consumer.accept(transform.apply(str));
    }
  }

  private boolean isNot(JsonToken token, JsonToken status) {
    return token != JsonToken.VALUE_NULL && token != status;
  }
}
