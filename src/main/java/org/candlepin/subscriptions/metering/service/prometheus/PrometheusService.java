/*
 * Copyright (c) 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.prometheus.ApiException;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.model.QueryResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.OffsetDateTime;

/**
 * Wraps prometheus specific API calls to make them more application specific.
 */
@Component
public class PrometheusService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);

    private ApiProvider apiProvider;

    public PrometheusService(ApiProvider prometheusApiProvider) {
        this.apiProvider = prometheusApiProvider;
    }

    public QueryResult runRangeQuery(String promQuery, OffsetDateTime start, OffsetDateTime end,
        Integer step, Integer timeout)
        throws ExternalServiceException {
        log.info("Fetching metrics from prometheus: {} -> {} [Step: {}]", start, end, step);
        try {
            // NOTE: While the ApiClient **should** in theory already encode the query,
            //       it does not handle the curly braces correctly causing issues
            //       when the request is made.
            //
            //       Also, the Prometheus APIs do not seem to like whitespace, even when encoded.
            String accountQuery = StringUtils.trimAllWhitespace(promQuery);
            log.debug("RAW Query: {}", accountQuery);
            String query = URLEncoder.encode(accountQuery, "UTF-8");
            log.debug("Running prometheus query: {}", query);
            return apiProvider.queryRangeApi().queryRange(query, start.toEpochSecond(),
                end.toEpochSecond(), Integer.toString(step), timeout);
        }
        catch (ApiException apie) {
            // ApiException message returned from prometheus server are huge and include the
            // HTML error page body. Just output the code here.
            throw new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                String.format("Prometheus API Error! CODE: %s", apie.getCode()),
                new ApiException(String.format("Prometheus API response code: %s", apie.getCode()))
            );
        }
        catch (UnsupportedEncodingException e) {
            throw new MeteringException("Unsupported encoding for specified PromQL.", e);
        }


    }

}
