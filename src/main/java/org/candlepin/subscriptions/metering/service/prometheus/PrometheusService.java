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

import com.google.common.net.UrlEscapers;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.prometheus.ApiException;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Wraps prometheus specific API calls to make them more application specific. */
@Component
public class PrometheusService {

  private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);

  private ApiProvider apiProvider;

  public PrometheusService(ApiProvider prometheusApiProvider) {
    this.apiProvider = prometheusApiProvider;
  }

  public QueryResult runRangeQuery(
      String promQL, OffsetDateTime start, OffsetDateTime end, Integer step, Integer timeout)
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
      return apiProvider
          .queryRangeApi()
          .queryRange(
              query, start.toEpochSecond(), end.toEpochSecond(), Integer.toString(step), timeout);
    } catch (ApiException apie) {
      // ApiException message returned from prometheus server are huge and include the
      // HTML error page body. Just output the code here.
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          String.format("Prometheus API Error! CODE: %s", apie.getCode()),
          new ApiException(String.format("Prometheus API response code: %s", apie.getCode())));
    }
  }

  public QueryResult runQuery(String promQL, OffsetDateTime time, Integer timeout)
      throws ExternalServiceException {
    log.info("Fetching metrics from prometheus: {}", time);
    try {
      String query = sanitizeQuery(promQL);
      log.debug("Running prometheus query: Time: {}, Query: {}", time.toEpochSecond(), query);
      return apiProvider.queryApi().query(query, time, timeout);
    } catch (ApiException apie) {
      // ApiException message returned from prometheus server are huge and include the
      // HTML error page body. Just output the code here.
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          String.format("Prometheus API Error! CODE: %s", apie.getCode()),
          new ApiException(String.format("Prometheus API response code: %s", apie.getCode())));
    }
  }

  private String sanitizeQuery(String promQL) {
    // NOTE: While the ApiClient **should** in theory already encode the query,
    //       it does not handle the curly braces correctly causing issues
    //       when the request is made.
    return UrlEscapers.urlFragmentEscaper().escape(promQL);
  }
}
