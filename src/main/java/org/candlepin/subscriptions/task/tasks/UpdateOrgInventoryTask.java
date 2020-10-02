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
package org.candlepin.subscriptions.task.tasks;

import org.candlepin.subscriptions.controller.InventoryController;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.pinhead.client.ApiException;
import org.candlepin.subscriptions.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A task that retrieves the Consumer data associated with an organization from Pinhead, and sends
 * that data into the insights host inventory.
 */
public class UpdateOrgInventoryTask implements Task {

    private static Logger log = LoggerFactory.getLogger(UpdateOrgInventoryTask.class);

    private String orgId;
    private String offset;
    private InventoryController controller;

    public UpdateOrgInventoryTask(InventoryController controller, String orgId, String offset) {
        this.orgId = orgId;
        this.offset = offset;
        this.controller = controller;
    }

    @Override
    public void execute() {
        log.info("Updating inventory for org {} with offset {}", orgId, offset);
        try {
            controller.updateInventoryForOrg(orgId, offset);
        }
        catch (MissingAccountNumberException e) {
            log.warn("Org {} is missing account number", orgId);
        }
        catch (ApiException e) {
            log.error("Exception calling pinhead", e);
        }
    }
}
