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
package org.candlepin.insights.pinhead.client;

import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.InstalledProducts;
import org.candlepin.insights.pinhead.client.model.OrgInventory;
import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Stub class implementing PinheadApi that we can use for development against if the real thing is unavailable
 */
public class StubPinheadApi extends PinheadApi {
    private static Logger log = LoggerFactory.getLogger(StubPinheadApi.class);

    @Override
    public OrgInventory getConsumersForOrg(String orgId, Integer perPage, String offset) throws ApiException {
        OrgInventory inventory = new OrgInventory();

        Consumer consumer1 = new Consumer();
        consumer1.setUuid(UUID.randomUUID().toString());
        consumer1.setHypervisorName("hypervisor1.test.com");
        consumer1.getFacts().put("network.fqdn", "host1.test.com");
        consumer1.getFacts().put("dmi.system.uuid", UUID.randomUUID().toString());
        consumer1.getFacts().put("ip-addresses", "192.168.1.1, 10.0.0.1");
        consumer1.getFacts().put("mac-addresses", "00:00:00:00:00:00, ff:ff:ff:ff:ff:ff");
        consumer1.getFacts().put("cpu.cpu_socket(s)", "2");
        consumer1.getFacts().put("cpu.core(s)_per_socket", "2");
        consumer1.getFacts().put("memory.memtotal", "32757812");
        consumer1.getFacts().put("uname.machine", "x86_64");
        consumer1.getFacts().put("virt.is_guest", "True");
        InstalledProducts product = new InstalledProducts();
        product.setProductId(72L);
        consumer1.getInstalledProducts().add(product);

        Consumer consumer2 = new Consumer();
        consumer2.setUuid(UUID.randomUUID().toString());
        consumer2.getFacts().put("network.fqdn", "host2.test.com");

        inventory.getConsumers().add(consumer1);
        inventory.getConsumers().add(consumer2);
        log.info("Returning canned pinhead response: {}", inventory);
        return inventory;
    }
}
