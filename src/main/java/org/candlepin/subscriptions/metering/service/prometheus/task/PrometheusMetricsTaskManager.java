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
package org.candlepin.subscriptions.metering.service.prometheus.task;

import org.candlepin.subscriptions.metering.service.prometheus.PrometheusAccountSource;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskDescriptor.TaskDescriptorBuilder;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import javax.transaction.Transactional;


/**
 * Produces task messages related to pulling metrics back from Telemeter.
 */
@Component
public class PrometheusMetricsTaskManager {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsTaskManager.class);

    private String topic;

    private TaskQueue queue;

    private PrometheusAccountSource accountSource;

    public PrometheusMetricsTaskManager(TaskQueue queue,
        @Qualifier("meteringTaskQueueProperties") TaskQueueProperties queueProps,
        PrometheusAccountSource accountSource) {
        log.info("Initializing metering manager. Topic: {}", queueProps.getTopic());
        this.queue = queue;
        this.topic = queueProps.getTopic();
        this.accountSource = accountSource;
    }

    public void updateOpenshiftMetricsForAccount(String account, OffsetDateTime start,
        OffsetDateTime end) {
        this.queue.enqueue(createOpenshiftMetricsTask(account, start, end));
    }

    @Transactional
    public void updateOpenshiftMetricsForAllAccounts(OffsetDateTime start, OffsetDateTime end) {
        try (Stream<String> accountStream = accountSource.getOpenShiftMarketplaceAccounts(end).stream()) {
            log.info("Queuing OpenShift metrics update for all configured accounts.");
            accountStream.forEach(account -> updateOpenshiftMetricsForAccount(account, start, end));
            log.info("Done queuing updates of OpenShift metrics");
        }
    }

    private TaskDescriptor createOpenshiftMetricsTask(String account, OffsetDateTime start,
        OffsetDateTime end) {
        log.debug("ACCOUNT: {} START: {} END: {}", account, start, end);
        TaskDescriptorBuilder builder = TaskDescriptor.builder(TaskType.OPENSHIFT_METRICS_COLLECTION, topic)
            .setSingleValuedArg("account", account)
            .setSingleValuedArg("start", start.toString());

        if (end != null) {
            builder.setSingleValuedArg("end", end.toString());
        }
        return builder.build();
    }

}
