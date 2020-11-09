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
package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.spring.QueueProfile;
import org.candlepin.subscriptions.task.TaskManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Exposes the ability to trigger a tally for an account from JMX.
 */
@Component
@QueueProfile
@ManagedResource
public class TallyJmxBean {

    private static final Logger log = LoggerFactory.getLogger(TallyJmxBean.class);

    private final TaskManager tasks;

    public TallyJmxBean(TaskManager taskManager) {
        this.tasks = taskManager;
    }

    @ManagedOperation(description = "Trigger a tally for an account")
    public void tallyAccount(String accountNumber) {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Tally for account {} triggered over JMX by {}", accountNumber, principal);
        tasks.updateAccountSnapshots(accountNumber);
    }

    @ManagedOperation(description = "Trigger tally for all configured accounts")
    public void tallyConfiguredAccounts() {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Tally for all accounts triggered over JMX by {}", principal);
        tasks.updateSnapshotsForAllAccounts();
    }
}
