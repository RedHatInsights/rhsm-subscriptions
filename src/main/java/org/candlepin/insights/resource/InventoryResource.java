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

import org.candlepin.insights.api.model.OrgInventory;
import org.candlepin.insights.api.resources.InventoryApi;
import org.candlepin.insights.controller.InventoryController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * The inventory API implementation.
 */
@Component
@Validated
public class InventoryResource implements InventoryApi {

    private static Logger log = LoggerFactory.getLogger(InventoryResource.class);

    @Autowired
    private InventoryController inventoryController;

    @Override
    public OrgInventory getInventoryForOrg(@NotNull byte[] xRhIdentity, String orgId) {
        return new OrgInventory();
    }

    @Override
    public void updateInventoryForOrg(@NotNull byte[] xRhIdentity, String orgId) {
        inventoryController.updateInventoryForOrg(orgId);
    }
}
