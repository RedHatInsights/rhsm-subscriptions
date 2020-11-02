/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.task;

import org.candlepin.subscriptions.task.queue.ExecutorTaskQueue;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Executors;

/**
 * Instantiates/Configures the TaskQueue implementation that should be used. A bean should
 * be defined with an appropriate @ConditionalOnProperty annotation that matches a possible
 * rhsm-subscriptions.queue=:QUEUE_KEY property.
 *
 * If the 'queue' property is not set, the pass-through queue will be used.
 *
 * NOTE:
 * It is important that only one TaskQueue implementation can be returned based on the conditional
 * annotation.
 */
@EnableConfigurationProperties(TaskQueueProperties.class)
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
    @DependsOn("poolScheduler") // ensure work completes before shutting down the in-memory task queue
    @Profile("in-memory-queue")
    TaskQueue inMemoryQueue(TaskFactory taskFactory) {
        log.info("Configuring an in-memory task queue.");
        return new ExecutorTaskQueue(
            Executors.newFixedThreadPool(taskQueueProperties().getExecutorTaskQueueThreadLimit()),
            taskFactory
        );
    }

}
