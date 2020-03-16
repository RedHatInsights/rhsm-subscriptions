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
import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.UsageSnapshotProducer;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * A TaskManager is an injectable component that is responsible for putting tasks into
 * the TaskQueue. This is the entry point into the task system. Any class that would like
 * to initiate a task within rhsm-subscriptions, should inject this class and call the appropriate
 * method.
 */
@Component
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
    public void updateSnapshotsForAllAccounts() {
        List<String> accountList;
        try {
            accountList = accountListSource.list();
        }
        catch (IOException ioe) {
            throw new TaskManagerException("Could not list accounts for update snapshot task generation",
                ioe);
        }

        int accountBatchSize = appProperties.getAccountBatchSize();
        log.info("Queuing snapshot production for {} accounts in batches of {}.", accountList.size(),
            accountBatchSize);

        for (List<String> accounts : Iterables.partition(accountList, accountBatchSize)) {
            if (log.isDebugEnabled()) {
                log.debug("Queuing snapshot updates for accounts: {}", String.join(",", accounts));
            }

            try {
                queue.enqueue(
                    TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTaskGroup())
                    .setArg("accounts", accounts)
                    .build()
                );
            }
            catch (Exception e) {
                log.error("Could not queue snapshot updates for accounts: {}", String.join(",", accounts), e);
            }
        }
        log.info("Done queuing snapshot production for accounts.");
    }
}
