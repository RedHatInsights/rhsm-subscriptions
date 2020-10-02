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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.controller.InventoryController;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.exception.RhsmConduitException;
import org.candlepin.subscriptions.pinhead.client.ApiException;
import org.candlepin.subscriptions.task.TaskManager;
import org.candlepin.subscriptions.utilization.api.model.OrgInventory;
import org.candlepin.subscriptions.utilization.api.resources.InventoriesApi;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import io.micrometer.core.annotation.Timed;

import javax.ws.rs.core.Response;

/**
 * The inventories API implementation.
 */
@Component
@Validated
@Profile("rhsm-conduit")
public class InventoriesResource implements InventoriesApi {

    private final InventoryController inventoryController;
    private final TaskManager tasks;

    public InventoriesResource(InventoryController inventoryController, TaskManager tasks) {
        this.inventoryController = inventoryController;
        this.tasks = tasks;
    }

    @Override
    @Timed("rhsm-conduit.get.inventory")
    public OrgInventory getInventoryForOrg(String orgId, String offset) {
        try {
            return inventoryController.getInventoryForOrg(orgId, offset);
        }
        catch (ApiException e) {
            throw new RhsmConduitException(
                ErrorCode.PINHEAD_SERVICE_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Error while fetching inventory report for orgId %s offset %s", orgId, offset),
                e
            );
        }
        catch (MissingAccountNumberException e) {
            throw new RhsmConduitException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Systems are missing account number in orgId %s", orgId),
                e
            );
        }
    }

    @Override
    @Timed("rhsm-conduit.update.inventory")
    public void updateInventoryForOrg(String orgId) {
        tasks.updateOrgInventory(orgId);
    }
}
