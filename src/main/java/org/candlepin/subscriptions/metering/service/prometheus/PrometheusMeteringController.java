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
import org.springframework.util.StringUtils;

import io.micrometer.core.annotation.Timed;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * A controller class that defines the business logic related to any metrics that are gathered.
 */
@Component
public class PrometheusMeteringController {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMeteringController.class);

    private final PrometheusService prometheusService;
    private final EventController eventController;
    private final ApplicationClock clock;
    private final PrometheusMetricsPropeties metricProperties;
    private final RetryTemplate openshiftRetry;

    public PrometheusMeteringController(ApplicationClock clock, PrometheusMetricsPropeties metricProperties,
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
        openshiftRetry.execute(context -> {
            try {
                // Reset the start/end dates to ensure they span a complete hour.
                // NOTE: If the prometheus query step changes, we will need to adjust this.
                OffsetDateTime queryStart = clock.startOfHour(start);
                OffsetDateTime queryEnd = clock.endOfHour(end);

                log.info("Collecting labels for OpenShift metrics...");
                Map<LabelSetKey, Map<String, String>> labels = getSubscriptionLabels(account, queryStart,
                    queryEnd);
                if (labels.isEmpty()) {
                    log.info("No subscription labels found. No events will be generated.");
                }

                log.info("Collecting OpenShift metrics and generating events");
                int eventCount = generateEventsFromMetrics(labels, account, queryStart, queryEnd);
                log.info("Created {} OpenShift metric events for account {}.", eventCount, account);
                return null;
            }
            catch (Exception e) {
                log.warn("Exception thrown while updating OpenShift metrics. [Attempt: {}]: {}",
                    context.getRetryCount() + 1, e.getMessage());
                throw e;
            }
        });
    }

    private Map<LabelSetKey, Map<String, String>> getSubscriptionLabels(String account,
        OffsetDateTime start, OffsetDateTime end) {
        OpenshiftMetricProperties openshiftProperties = metricProperties.getOpenshift();
        Map<LabelSetKey, Map<String, String>> labelData = new HashMap<>();

        QueryResult labelMetrics = prometheusService.runRangeQuery(
            String.format(openshiftProperties.getSubscriptionLabelPromQL(), account),
            start,
            end,
            openshiftProperties.getStep(),
            openshiftProperties.getQueryTimeout()
        );

        if (StatusType.ERROR.equals(labelMetrics.getStatus())) {
            throw new MeteringException(
                String.format("Unable to fetch openshift metrics: %s", labelMetrics.getError())
            );
        }

        labelMetrics.getData().getResult().forEach(r -> {
            // Just need the label values from here.
            Map<String, String> labels = r.getMetric();
            String clusterId = labels.getOrDefault("_id", "");

            r.getValues().forEach(measurement -> {
                BigDecimal time = measurement.get(0);
                labelData.put(new LabelSetKey(clusterId, time), labels);
            });
        });

        return labelData;
    }

    private int generateEventsFromMetrics(Map<LabelSetKey, Map<String, String>> labelSets,
        String account, OffsetDateTime start, OffsetDateTime end) {
        OpenshiftMetricProperties openshiftProperties = metricProperties.getOpenshift();
        log.info("Collecting metrics!");
        QueryResult metricData = prometheusService.runRangeQuery(
            // Substitute the account number into the query. The query is expected to
            // contain %s for replacement.
            String.format(openshiftProperties.getMetricPromQL(), account),
            start,
            end,
            openshiftProperties.getStep(),
            openshiftProperties.getQueryTimeout()
        );

        if (StatusType.ERROR.equals(metricData.getStatus())) {
            throw new MeteringException(
                String.format("Unable to fetch openshift metrics: %s", metricData.getError())
            );
        }

        List<Event> events = new LinkedList<>();
        int totalEvents = 0;
        for (QueryResultDataResult r : metricData.getData().getResult()) {
            Map<String, String> metricLabels = r.getMetric();
            String clusterId = metricLabels.getOrDefault("_id", "");
            if (!StringUtils.hasText(clusterId)) {
                // Can't map this metric without a cluster ID. Not likely to happen but we
                // will log just in case.
                log.warn("Skipping metric due to missing cluster ID.");
                continue;
            }

            for (List<BigDecimal> measurement : r.getValues()) {
                BigDecimal time = measurement.get(0);
                BigDecimal value = measurement.get(1);
                if (time == null || value == null) {
                    // Not likely to happen, but will log just in case.
                    log.warn("Skipping metric since time/value pair was invalid: {}/{}", time, value);
                    continue;
                }

                Map<String, String> clusterLabels = labelSets.get(new LabelSetKey(clusterId, time));
                if (clusterLabels == null) {
                    // This could potentially happen if the metric existed but the
                    // Labels weren't reported yet.
                    log.warn("Found OpenShift metric but could not associate cluster {} " +
                        "with any labels. Time: {} Value: {}", clusterId, time, value);
                    continue;
                }

                Event event = MeteringEventFactory.openShiftClusterCores(
                    clusterLabels.get("ebs_account"),
                    clusterLabels.get("_id"),
                    clusterLabels.get("support"),
                    clusterLabels.get("usage"),
                    clock.dateFromUnix(time),
                    value.doubleValue()
                );
                events.add(event);
                totalEvents++;

                if (events.size() >= openshiftProperties.getEventBatchSize()) {
                    log.info("Saving {} events", events.size());
                    eventController.saveAll(events);
                    events.clear();
                }
            }
        }

        // Flush the remainder
        if (!events.isEmpty()) {
            log.info("Saving events: {}", events.size());
            eventController.saveAll(events);
        }
        return totalEvents;
    }

    private class LabelSetKey {
        private String clusterId;
        private BigDecimal date;

        public LabelSetKey(String clusterId, BigDecimal date) {
            this.clusterId = clusterId;
            this.date = date;
        }

        public String getClusterId() {
            return clusterId;
        }

        public BigDecimal getDate() {
            return date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LabelSetKey that = (LabelSetKey) o;
            return Objects.equals(clusterId, that.clusterId) && Objects.equals(date, that.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clusterId, date);
        }
    }
}
