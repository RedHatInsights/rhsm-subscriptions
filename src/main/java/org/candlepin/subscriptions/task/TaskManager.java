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
package org.candlepin.subscriptions.task;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.spring.QueueProfile;
import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.tally.UsageSnapshotProducer;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A TaskManager is an injectable component that is responsible for putting tasks into
 * the TaskQueue. This is the entry point into the task system. Any class that would like
 * to initiate a task within rhsm-subscriptions, should inject this class and call the appropriate
 * method.
 */
@Component
@QueueProfile
public class TaskManager {
    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    @Autowired
    private ApplicationProperties appProperties;

    @Autowired
    private TaskQueueProperties taskQueueProperties;

    @Autowired
    private TaskQueue queue;

    @Autowired
    private UsageSnapshotProducer snapshotProducer;

    @Autowired
    private AccountListSource accountListSource;

    /**
     * Initiates a task that will update the snapshots for the specified account.
     *
     * @param accountNumber the account number in which to update.
     */
    @SuppressWarnings("indentation")
    public void updateAccountSnapshots(String accountNumber) {
        queue.enqueue(
            TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTaskGroup())
                .setSingleValuedArg("accounts", accountNumber)
                .build()
        );
    }

    /**
     * Queue up tasks to update the snapshots for all configured accounts.
     *
     * @throws TaskManagerException
     */
    @Transactional
    public void updateSnapshotsForAllAccounts() {
        int accountBatchSize = appProperties.getAccountBatchSize();
        AccountUpdateQueue updateQueue = new AccountUpdateQueue(queue, accountBatchSize);

        try (Stream<String> accountStream = accountListSource.syncableAccounts()) {
            log.info("Queuing snapshot production in batches of {}.", accountBatchSize);

            AtomicInteger count = new AtomicInteger(0);
            accountStream.forEach(account -> {
                updateQueue.queue(account);
                count.addAndGet(1);
            });

            // The final group of accounts might have be less than the batch size
            // and need to be flushed.
            if (!updateQueue.isEmpty()) {
                updateQueue.flush();
            }

            log.info("Done queuing snapshot production for {} accounts.", count.intValue());
        }
        catch (AccountListSourceException e) {
            throw new TaskManagerException("Could not list accounts for update snapshot task generation", e);
        }
    }

    /**
     * A class that is used to queue up account numbers as they are streamed from the DB
     * so that they can be sent for updates in the configured batches.
     */
    private class AccountUpdateQueue {
        private int batchSize;
        private TaskQueue taskQueue;
        private List<String> queuedAccounts;

        public AccountUpdateQueue(TaskQueue taskQueue, int batchSize) {
            this.taskQueue = taskQueue;
            this.batchSize = batchSize;
            this.queuedAccounts = new LinkedList<>();
        }

        public void queue(String account) {
            queuedAccounts.add(account);
            if (queuedAccounts.size() == batchSize) {
                flush();
            }
        }

        public void flush() {
            try {
                taskQueue.enqueue(
                    TaskDescriptor
                    .builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTaskGroup())
                    // clone the list so that we can be sure that we don't clear references
                    // out from under the task queue should delivery be delayed for any reason.
                    .setArg("accounts", new ArrayList<>(queuedAccounts))
                    .build()
                );
            }
            catch (Exception e) {
                log.error("Could not queue snapshot updates for accounts: {}",
                    String.join(",", queuedAccounts), e);
            }
            queuedAccounts.clear();
        }

        public boolean isEmpty() {
            return queuedAccounts.isEmpty();
        }

    }
}
