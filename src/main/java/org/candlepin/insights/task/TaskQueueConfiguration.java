/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.task;

import org.candlepin.insights.task.queue.TaskQueue;
import org.candlepin.insights.task.queue.passthrough.PassThroughTaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Instantiates/Configures the TaskQueue implementation that should be used. A bean should
 * be defined with an appropriate @ConditionalOnProperty annotation that matches a possible
 * rhsm-conduit.queue=:QUEUE_KEY property.
 *
 * If the 'queue' property is not set, the pass-through queue will be used.
 *
 * NOTE:
 * It is important that only one TaskQueue implementation can be returned based on the conditional
 * annotation.
 */
@Configuration
public class TaskQueueConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueConfiguration.class);

    public static final String TASK_GROUP = "rhsm-conduit-tasks";

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "pass-through",
        matchIfMissing = true)
    TaskQueue passThroughQueue(TaskWorker worker) {
        log.info("Configuring a pass-through task queue.");
        return new PassThroughTaskQueue(worker);
    }

}
