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
package org.candlepin.insights.resource;

import org.candlepin.insights.api.model.ConsumerInventory;
import org.candlepin.insights.api.model.OrgInventory;
import org.candlepin.insights.api.resources.InventoriesApi;
import org.candlepin.insights.controller.InventoryController;
import org.candlepin.insights.task.TaskManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.Date;

import javax.validation.constraints.NotNull;


/**
 * The inventories API implementation.
 */
@Component
@Validated
public class InventoriesResource implements InventoriesApi {

    @Autowired
    private InventoryController inventoryController;

    @Autowired
    private TaskManager tasks;

    @Override
    public OrgInventory getInventoryForOrg(@NotNull byte[] xRhIdentity, String orgId) {
        return new OrgInventory().consumerInventories(Collections.singletonList(
            new ConsumerInventory()
                .consumerType("system")
                .lastCheckin(new Date())
                .ownerAccountKey(orgId)
        ));
    }

    @Override
    public void updateInventoryForOrg(@NotNull byte[] xRhIdentity, String orgId) {
        tasks.updateOrgInventory(orgId);
    }
}
