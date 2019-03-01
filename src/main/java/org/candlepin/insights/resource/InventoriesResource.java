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
