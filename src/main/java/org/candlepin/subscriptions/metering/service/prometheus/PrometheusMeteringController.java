/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
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
import java.util.Collection;
import java.util.HashMap;
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
    private final PrometheusMetricsProperties metricProperties;
    private final RetryTemplate openshiftRetry;

    public PrometheusMeteringController(ApplicationClock clock, PrometheusMetricsProperties metricProperties,
        PrometheusService service, EventController eventController,
        @Qualifier("openshiftMetricRetryTemplate") RetryTemplate openshiftRetry) {
        this.clock = clock;
        this.metricProperties = metricProperties;
        this.prometheusService = service;
        this.eventController = eventController;
        this.openshiftRetry = openshiftRetry;
    }

    // Suppressing this sonar issue because we need to log plus throw an exception on retry
    // otherwise we never know that we have failed during the retry cycle until all attempts
    // are exhausted.
    @SuppressWarnings("java:S2139")
    @Timed("rhsm-subscriptions.metering.openshift")
    @Transactional
    public void collectOpenshiftMetrics(String account, OffsetDateTime start, OffsetDateTime end) {
        MetricProperties openshiftProperties = metricProperties.getOpenshift();
        // Reset the start/end dates to ensure they span a complete hour.
        // NOTE: If the prometheus query step changes, we will need to adjust this.
        OffsetDateTime startDate = clock.startOfHour(start);
        OffsetDateTime endDate = clock.endOfHour(end);
        openshiftRetry.execute(context -> {
            try {
                log.info("Collecting OpenShift metrics");

                QueryResult metricData = prometheusService.runRangeQuery(
                    // Substitute the account number into the query. The query is expected to
                    // contain %s for replacement.
                    String.format(openshiftProperties.getMetricPromQL(), account),
                    startDate,
                    endDate,
                    openshiftProperties.getStep(),
                    openshiftProperties.getQueryTimeout()
                );

                if (StatusType.ERROR.equals(metricData.getStatus())) {
                    throw new MeteringException(
                        String.format("Unable to fetch OpenShift metrics: %s", metricData.getError())
                    );
                }

                Map<EventKey, Event> existing = eventController.mapEventsInTimeRange(
                    account,
                    MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_SOURCE,
                    MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_TYPE,
                    startDate,
                    endDate
                );
                log.debug("Found {} existing events.", existing.size());

                Map<EventKey, Event> events = new HashMap<>();
                for (QueryResultDataResult r : metricData.getData().getResult()) {
                    Map<String, String> labels = r.getMetric();
                    String clusterId = labels.get("_id");
                    String sla = labels.get("support");
                    String usage = labels.get("usage");

                    // For the openshift metrics, we expect our results to be a 'matrix'
                    // vector [(instant_time,value), ...] so we only look at the result's getValues()
                    // data.
                    for (List<BigDecimal> measurement : r.getValues()) {
                        BigDecimal time = measurement.get(0);
                        BigDecimal value = measurement.get(1);

                        OffsetDateTime eventTermDate = clock.dateFromUnix(time);
                        // Need to subtract the step because we are averaging and the metric value
                        // actually represents the end of the measured period. The start of the event
                        // should be at the beginning.
                        OffsetDateTime eventDate =
                            eventTermDate.minusSeconds(metricProperties.getOpenshift().getStep());

                        Event event = createOrUpdateEvent(existing, account, clusterId, sla, usage,
                            eventDate, eventTermDate, value);
                        events.put(EventKey.fromEvent(event), event);
                    }
                }

                eventController.saveAll(events.values());
                log.info("Persisted {} events for OpenShift metrics.", events.size());

                // Delete any stale events found during the period.
                deleteStaleEvents(existing.values());
                return null;
            }
            catch (Exception e) {
                log.warn("Exception thrown while updating OpenShift metrics. [Attempt: {}]: {}",
                    context.getRetryCount() + 1, e.getMessage());
                throw e;
            }
        });
    }

    @SuppressWarnings("java:S107")
    private Event createOrUpdateEvent(Map<EventKey, Event> existing, String account, String instanceId,
        String sla, String usage, OffsetDateTime measuredDate, OffsetDateTime expired, BigDecimal value) {
        EventKey lookupKey = new EventKey(
            account,
            MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_SOURCE,
            MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_TYPE,
            instanceId,
            measuredDate
        );

        Event event = existing.remove(lookupKey);
        if (event == null) {
            event = new Event();
        }
        MeteringEventFactory.updateOpenShiftClusterCores(event, account, instanceId, sla, usage,
            measuredDate, expired, value.doubleValue());
        return event;
    }

    private void deleteStaleEvents(Collection<Event> toDelete) {
        if (!toDelete.isEmpty()) {
            log.info("Deleting {} stale OpenShift metric events.", toDelete.size());
            eventController.deleteEvents(toDelete);
        }
    }
}
