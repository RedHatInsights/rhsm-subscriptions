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
package org.candlepin.subscriptions.pinhead.client;

import org.candlepin.subscriptions.pinhead.client.model.Consumer;
import org.candlepin.subscriptions.pinhead.client.model.InstalledProducts;
import org.candlepin.subscriptions.pinhead.client.model.OrgInventory;
import org.candlepin.subscriptions.pinhead.client.model.Pagination;
import org.candlepin.subscriptions.pinhead.client.model.Status;
import org.candlepin.subscriptions.pinhead.client.resources.PinheadApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Stub class implementing PinheadApi that we can use for development against if the real thing is unavailable
 */
public class StubPinheadApi extends PinheadApi {
    private static Logger log = LoggerFactory.getLogger(StubPinheadApi.class);

    @Override
    public OrgInventory getConsumersForOrg(String orgId, Integer perPage, String offset,
        String lastCheckinAfter) throws ApiException {
        OrgInventory inventory = new OrgInventory();

        Consumer consumer1 = new Consumer();
        consumer1.setUuid(UUID.randomUUID().toString());
        consumer1.setOrgId(orgId);
        consumer1.setAccountNumber("ACCOUNT_1");
        consumer1.setHypervisorName("hypervisor1.test.com");
        consumer1.setServiceLevel("Premium");
        consumer1.getFacts().put("network.fqdn", "host1.test.com");
        consumer1.getFacts().put("dmi.system.uuid", UUID.randomUUID().toString());
        consumer1.getFacts().put("Ip-addresses", "192.168.1.1, 10.0.0.1");
        consumer1.getFacts().put("Mac-addresses", "00:00:00:00:00:00, ff:ff:ff:ff:ff:ff");
        consumer1.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer1.getFacts().put("cpu.core(s)_per_socket", "2");
        consumer1.getFacts().put("memory.memtotal", "32757812");
        consumer1.getFacts().put("uname.machine", "x86_64");
        consumer1.getFacts().put("virt.is_guest", "True");
        consumer1.getFacts().put("ocm.units", "Sockets");
        InstalledProducts product = new InstalledProducts();
        product.setProductId(72L);
        consumer1.getInstalledProducts().add(product);

        Consumer consumer2 = new Consumer();
        consumer2.setUuid(UUID.randomUUID().toString());
        consumer2.setOrgId(orgId);
        consumer2.setAccountNumber("ACCOUNT_1");
        consumer2.getFacts().put("network.fqdn", "host2.test.com");

        if (offset == null) {
            inventory.getFeeds().add(consumer1);
            inventory.status(new Status().pagination(new Pagination().nextOffset("next-offset")));
        }
        else {
            inventory.getFeeds().add(consumer2);
        }
        log.info("Returning canned pinhead response: {}", inventory);
        return inventory;
    }
}
