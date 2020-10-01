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
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.pinhead.client.ApiException;
import org.candlepin.subscriptions.task.TaskManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoriesResourceTest {
    @Mock
    InventoryController controller;

    @Mock
    TaskManager manager;

    @Test
    void testGetInventoryCallsInventoryController()
        throws MissingAccountNumberException, ApiException {
        InventoriesResource inventories = new InventoriesResource(controller, manager);
        inventories.getInventoryForOrg("org-1234", null);
        Mockito.verify(controller).getInventoryForOrg(Mockito.eq("org-1234"), Mockito.eq(null));
    }

    @Test
    void testUpdateInventoryDelegatesToTask() {
        InventoriesResource inventories = new InventoriesResource(controller, manager);
        inventories.updateInventoryForOrg("org-1234");
        Mockito.verify(manager).updateOrgInventory(Mockito.eq("org-1234"));
    }
}
