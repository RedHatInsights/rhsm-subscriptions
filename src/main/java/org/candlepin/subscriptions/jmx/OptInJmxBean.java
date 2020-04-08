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
package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.controller.OptInController;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Exposes the ability to perform OptIn operations.
 */
@Component
@Profile({"api"})
@ManagedResource
public class OptInJmxBean {
    private static final Logger log = LoggerFactory.getLogger(OptInJmxBean.class);

    private final OptInController controller;

    public OptInJmxBean(OptInController controller) {
        this.controller = controller;
    }

    @ManagedOperation(description = "Fetch an opt in configuration")
    @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
    @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
    public String getOptInConfig(String accountNumber, String orgId) {
        return controller.getOptInConfig(accountNumber, orgId).toString();
    }

    @ManagedOperation(description = "Delete opt in configuration")
    @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
    @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
    public void optOut(String accountNumber, String orgId) {
        controller.optOut(accountNumber, orgId);
    }

    @ManagedOperation(description = "Create or update an opt in configuration. This operation is idempotent")
    @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
    @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
    @ManagedOperationParameter(name = "enableTallySync", description = "Turn on Tally syncing")
    @ManagedOperationParameter(name = "enableTallyReporting", description = "Turn on Tally reporting")
    @ManagedOperationParameter(name = "enableConduitSync", description = "Turn on Conduit syncing")
    public String createOrUpdateOptInConfig(String accountNumber, String orgId, boolean enableTallySync,
        boolean enableTallyReporting, boolean enableConduitSync) {
        log.debug("Creating OptInConfig over JMX for account {}, org {}", accountNumber, orgId);
        OptInConfig config = controller.optIn(accountNumber, orgId, OptInType.JMX, enableTallySync,
            enableTallyReporting, enableConduitSync);

        String text = "Completed opt in for account %s and org %s:\n%s";
        return String.format(text, accountNumber, orgId, config.toString());
    }
}
