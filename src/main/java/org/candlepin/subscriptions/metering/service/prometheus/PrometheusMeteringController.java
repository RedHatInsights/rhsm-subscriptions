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

import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.annotation.Timed;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * A controller class that defines the business logic related to any metrics that are gathered.
 */
@Component
public class PrometheusMeteringController {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMeteringController.class);

    private final PrometheusService prometheusService;
    private final EventController eventController;
    private final ApplicationClock clock;
    private final PrometheusMetricPropeties metricProperties;
    private final RetryTemplate openshiftRetry;

    public PrometheusMeteringController(ApplicationClock clock, PrometheusMetricPropeties metricProperties,
        PrometheusService service, EventController eventController,
        @Qualifier("prometheusMeteringRetryTemplate") RetryTemplate openshiftRetry) {
        this.clock = clock;
        this.metricProperties = metricProperties;
        this.prometheusService = service;
        this.eventController = eventController;
        this.openshiftRetry = openshiftRetry;
    }

    @Timed("rhsm-subscriptions.metering.openshift")
    @Transactional
    public void collectOpenshiftMetrics(String account, OffsetDateTime start, OffsetDateTime end) {
        openshiftRetry.execute(context -> {
            try {
                log.info("Collecting openshift metrics");
                // Reset the start/end dates to ensure they span a complete hour.
                // NOTE: If the prometheus query step changes, we will need to adjust this.
                QueryResult metricData = prometheusService.getOpenshiftData(account, clock.startOfHour(start),
                    clock.endOfHour(end));

                if (StatusType.ERROR.equals(metricData.getStatus())) {
                    throw new MeteringException(
                        String.format("Unable to fetch openshift metrics: %s", metricData.getError())
                    );
                }

                // Given the possibility of a very large number of events, we will batch persist them
                // to provide the ability to tweak them.
                List<Event> events = new LinkedList<>();
                metricData.getData().getResult().forEach(r -> {
                    Map<String, String> labels = r.getMetric();
                    String clusterId = labels.getOrDefault("_id", "");
                    String sla = labels.getOrDefault("support", "");

                    // For the openshift metrics, we expect our results to be a 'matrix'
                    // vector [(instant_time,value), ...] so we only look at the result's getValues()
                    // data.
                    r.getValues().forEach(measurement -> {
                        BigDecimal time = measurement.get(0);
                        BigDecimal value = measurement.get(1);
                        Event event = MeteringEventFactory.openShiftClusterCores(account, clusterId, sla,
                            clock.dateFromUnix(time), value.doubleValue());
                        events.add(event);

                        if (log.isDebugEnabled()) {
                            log.debug(event.toString());
                        }

                        if (events.size() >= metricProperties.getEventBatchSize()) {
                            log.info("Saving {} events", events.size());
                            eventController.saveAll(events);
                            events.clear();
                        }
                    });
                });

                // Flush the remainder
                if (!events.isEmpty()) {
                    log.debug("Saving remaining events: {}", events.size());
                    eventController.saveAll(events);
                }

                return null;
            }
            catch (Exception e) {
                log.warn("Exception thrown while updating openshift metrics. [Attempt: {}]: {}",
                    context.getRetryCount() + 1, e.getMessage());
                throw e;
            }
        });
    }

}
