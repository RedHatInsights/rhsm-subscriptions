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
package org.candlepin.insights.inventory;

import org.candlepin.insights.exception.inventory.InventoryServiceException;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * A wrapper for the insights inventory client.
 *
 * If we get to the point where we are making multiple manipulations to the data stream as it flows through
 * this class consider
 * <code>
 * public interface ConduitVisitor {
 *     default FactSet visit(FactSet factSet) {
 *         return factSet;
 *     }
 *
 *     default CreateHostIn visit(CreateHostIn createHostIn) {
 *         return createHostIn;
 *     }
 *
 *     default BulkHostOut visit(BulkHostOut bulkHostOut) {
 *         return bulkHostOut;
 *     }
 * </code>
 *
 * The visit methods can then get called at the appropriate places in sendHostUpdate and createHost
 * allowing us to externalize manipulations to the implementation(s) of ConduitVisitor.
 */
public class DefaultInventoryService extends InventoryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultInventoryService.class);

    private final HostsApi hostsInventoryApi;

    public DefaultInventoryService(HostsApi hostsInventoryApi) {
        this.hostsInventoryApi = hostsInventoryApi;
    }

    public void sendHostUpdate(List<ConduitFacts> facts) {

        // The same timestamp for the whole batch
        OffsetDateTime now = OffsetDateTime.now();
        List<CreateHostIn> hostsToSend = facts.stream()
            .map(x -> createHost(x, now))
            .collect(Collectors.toList());

        try {
            BulkHostOut hosts = hostsInventoryApi.apiHostAddHostList(hostsToSend);
            log.debug("Finished updating hosts: {}", hosts);
        }
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
        }
    }

}
