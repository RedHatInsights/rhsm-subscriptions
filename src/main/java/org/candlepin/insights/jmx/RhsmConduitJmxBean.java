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
package org.candlepin.insights.jmx;

import org.candlepin.insights.controller.InventoryController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Exposes the ability to trigger a sync of a Candlepin org from JMX.
 */
@Component
@ManagedResource
public class RhsmConduitJmxBean {

    private static final Logger log = LoggerFactory.getLogger(RhsmConduitJmxBean.class);

    private final InventoryController controller;

    RhsmConduitJmxBean(InventoryController controller) {
        this.controller = controller;
    }

    @ManagedOperation(description = "Trigger a sync for a given Org ID")
    public void syncOrg(String orgId) {
        log.info("Starting JMX-initiated sync for org ID {}", orgId);
        try {
            controller.updateInventoryForOrg(orgId);
        }
        catch (Exception e) {
            log.error("Error during JMX-initiated sync for org ID {}", orgId, e);
        }
    }
}
