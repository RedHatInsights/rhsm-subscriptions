/*
 * Copyright Red Hat, Inc.
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
package org.candlepin.subscriptions.conduit.rhsm.client;

import java.util.UUID;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Consumer;
import org.candlepin.subscriptions.conduit.rhsm.client.model.InstalledProducts;
import org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Pagination;
import org.candlepin.subscriptions.conduit.rhsm.client.resources.RhsmApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub class implementing RhsmApi that we can use for development against if the real thing is
 * unavailable
 */
public class StubRhsmApi extends RhsmApi {
  private static Logger log = LoggerFactory.getLogger(StubRhsmApi.class);

  private static final String MAX_ALLOWED_MEMORY_ORG_ID = "maxAllowedMemoryOrg";
  private static final String GCP_ORG_ID = "gcpOrg";

  @Override
  public OrgInventory getConsumersForOrg(
      String xRhsmApiAccountID, Integer limit, String offset, String lastCheckinAfter)
      throws ApiException {

    if (xRhsmApiAccountID.equals("slowOrg123")) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    OrgInventory inventory = new OrgInventory();

    Consumer consumer1 = new Consumer();
    consumer1.setUuid(UUID.randomUUID().toString());
    consumer1.setOrgId(xRhsmApiAccountID);
    consumer1.setHypervisorName("hypervisor1.test.com");
    consumer1.setServiceLevel("Premium");
    consumer1.setReleaseVer("8.0");
    consumer1.getFacts().put("network.fqdn", "host1.test.com");
    consumer1.getFacts().put("dmi.system.uuid", UUID.randomUUID().toString());
    consumer1.getFacts().put("Ip-addresses", "192.168.1.1, 10.0.0.1");
    consumer1.getFacts().put("net.interface.eth0.ipv4_address", "192.168.1.1, 10.0.0.1");
    consumer1
        .getFacts()
        .put("net.interface.virbr0.ipv4_address_list", "192.168.122.1, ipv4ListTest");
    consumer1.getFacts().put("net.interface.lo.ipv4_address_list", "192.167.11.2");
    consumer1.getFacts().put("net.interface.lo.ipv4_address", "redacted, 192.167.11.2");
    consumer1.getFacts().put("net.interface.lo.ipv4_address", "redacted");
    consumer1.getFacts().put("net.interface.lo.ipv6_address", "fe80::250:56ff:febe:f55a,redacted");
    consumer1.getFacts().put("net.interface.lo.ipv6_address", "redacted");
    consumer1.getFacts().put("Mac-addresses", "00:00:00:00:00:00, ff:ff:ff:ff:ff:ff");
    consumer1.getFacts().put("cpu.cpu_socket(s)", "2");
    consumer1.getFacts().put("cpu.core(s)_per_socket", "2");
    consumer1.getFacts().put("cpu.cpu(s)", "3");
    consumer1.getFacts().put("cpu.thread(s)_per_core", "5");
    consumer1.getFacts().put("aws_instance_id", "123456");

    consumer1.getFacts().put("memory.memtotal", "32757812");
    consumer1.getFacts().put("uname.machine", "x86_64");
    consumer1.getFacts().put("virt.is_guest", "True");
    consumer1.getFacts().put("ocm.units", "Sockets");
    consumer1.getFacts().put("ocm.billing_model", "standard");
    consumer1.getFacts().put("distribution.name", "Red Hat Enterprise Linux Workstation");
    consumer1.getFacts().put("distribution.version", "6.3");
    consumer1.getFacts().put("azure_offer", "RHEL");
    consumer1.getFacts().put("conversions.activity", "conversion");
    InstalledProducts product = new InstalledProducts();
    product.setProductId("72");
    consumer1.getInstalledProducts().add(product);
    applyOrgIdUpdates(xRhsmApiAccountID, consumer1);

    Consumer consumer2 = new Consumer();
    String consumer2Uuid = UUID.randomUUID().toString();
    consumer2.setUuid(consumer2Uuid);
    consumer2.setOrgId(xRhsmApiAccountID);
    consumer2.getFacts().put("network.fqdn", "host2.test.com");

    Pagination pagination = new Pagination().offset(offset).limit(limit.longValue());
    if (offset == null) {
      inventory.addBodyItem(consumer1);
      pagination.count(1L);
    } else if (!consumer2Uuid.equals(offset)) {
      inventory.addBodyItem(consumer2);
      pagination.count(1L);
    } else {
      pagination.count(0L);
    }
    inventory.pagination(pagination);
    log.info("Returning canned rhsm response: {}", inventory);
    return inventory;
  }

  private void applyOrgIdUpdates(String orgId, Consumer consumer) {
    if (orgId.equals(MAX_ALLOWED_MEMORY_ORG_ID)) {
      consumer.getFacts().put("memory.memtotal", "8830587505648");
    }
    if (orgId.equals(GCP_ORG_ID)) {
      consumer.getFacts().remove("azure_offer");
      consumer.getFacts().put("dmi.bios.vendor", "Google");
      consumer.getFacts().put("gcp_license_codes", "7883559014960410759");
    }
  }
}
