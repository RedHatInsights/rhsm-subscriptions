/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.task;

import org.candlepin.insights.task.queue.ExecutorTaskProcessor;
import org.candlepin.insights.task.queue.ExecutorTaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.concurrent.Executors;

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
@EnableConfigurationProperties(TaskQueueProperties.class)
@PropertySource("classpath:/rhsm-conduit.properties")
@Configuration
public class TaskQueueConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueConfiguration.class);

    @Bean
    TaskQueueProperties taskQueueProperties() {
        return new TaskQueueProperties();
    }

    /**
     * Creates an in-memory queue, implemented with {@link java.util.concurrent.ThreadPoolExecutor}.
     *
     * Does not block while executing a task. Spin up a new thread for each task, only practically bound by
     * amount of memory available.
     */
    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "in-memory",
        matchIfMissing = true)
    ExecutorTaskQueue inMemoryQueue() {
        log.info("Configuring an in-memory task queue.");
        return new ExecutorTaskQueue();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.tasks", name = "queue", havingValue = "in-memory",
        matchIfMissing = true)
    ExecutorTaskProcessor inMemoryQueueProcessor(ExecutorTaskQueue queue, TaskFactory taskFactory) {
        return new ExecutorTaskProcessor(
            Executors.newFixedThreadPool(taskQueueProperties().getExecutorTaskQueueThreadLimit()),
            taskFactory,
            queue
        );
    }

}
