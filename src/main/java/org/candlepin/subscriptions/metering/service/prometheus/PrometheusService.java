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

    private final String openshiftMetricsQuery;
    private final int openshiftMetricStep;
    private final int requestTimeout;
    private ApiProvider apiProvider;

    public PrometheusService(PrometheusServicePropeties props, ApiProvider prometheusApiProvider) {
        // Query API does not seem to like whitespace, even when encoded.
        this.openshiftMetricsQuery = StringUtils.trimAllWhitespace(props.getOpenshiftMetricsPromQL());
        this.openshiftMetricStep = props.getOpenshiftMetricStep();
        this.requestTimeout = props.getRequestTimeout();
        this.apiProvider = prometheusApiProvider;
    }

    public QueryResult getOpenshiftData(String account, OffsetDateTime start, OffsetDateTime end)
        throws ApiException {
        try {
            // NOTE: While the ApiClient **should** in theory already encode the query,
            //       it does not handle the curly braces correctly causing issues
            //       when the request is made.
            String accountQuery = String.format(openshiftMetricsQuery, account);
            log.debug("RAW Query: {}", accountQuery);
            String query = URLEncoder.encode(accountQuery, "UTF-8");
            log.debug("Running prometheus query: {}", query);
            return apiProvider.queryRangeApi().queryRange(query, start.toEpochSecond(),
                end.toEpochSecond(), Integer.toString(openshiftMetricStep), requestTimeout);
        }
        catch (UnsupportedEncodingException e) {
            throw new ApiException(e);
        }
    }

}
