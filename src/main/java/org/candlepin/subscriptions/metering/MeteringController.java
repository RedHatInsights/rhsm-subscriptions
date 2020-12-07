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
package org.candlepin.subscriptions.metering;

import org.candlepin.subscriptions.metering.service.PrometheusService;
import org.candlepin.subscriptions.prometheus.ApiException;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.StatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;


/**
 * A controller class that defines the business logic related to any metrics that are gathered.
 */
@Component
public class MeteringController {

    private static final Logger log = LoggerFactory.getLogger(MeteringController.class);

    private final PrometheusService prometheusService;

    public MeteringController(PrometheusService service) {
        this.prometheusService = service;
    }

    public void reportOpenshiftMetrics(String account, OffsetDateTime start, OffsetDateTime end)
        throws ApiException {
        QueryResult metricData = prometheusService.getOpenshiftData(account, start, end);
        if (StatusType.ERROR.equals(metricData.getStatus())) {
            throw new MeteringException(
                String.format("Unable to fetch openshift metrics: %s", metricData.getError())
            );
        }

        metricData.getData().getResult().forEach(r -> {
            Map<String, String> labels = r.getMetric();
            String clusterId = labels.getOrDefault("_id", "");
            String serviceLevel = labels.getOrDefault("support", "");

            // For the openshift metrics, we expect our results to be an 'matrix'
            // vector [(instant_time,value), ...] so we only look at the result's getValues()
            // data.
            r.getValues().forEach(measurement -> {
                BigDecimal time = measurement.get(0);
                BigDecimal value = measurement.get(1);

                // TODO Persist events to the DB once the supporting code is ready.
                log.info("# PERSISTING EVENT -> Cluster: {}, SLA: {} [{}:{}]",
                    clusterId, serviceLevel, time, value);
            });
        });

    }
}
