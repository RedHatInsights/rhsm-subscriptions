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
package org.candlepin.subscriptions.tally;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.springframework.util.StringUtils;

/** Provides helper functions for test cases. */
public class InventoryHostFactTestHelper {

  private InventoryHostFactTestHelper() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  public static InventoryHostFacts createHypervisor(String orgId, Integer product) {
    InventoryHostFacts baseFacts = createBaseHost(orgId);
    if (product != null) {
      baseFacts.setProducts(product.toString());
    }
    return baseFacts;
  }

  public static InventoryHostFacts createGuest(
      String hypervisorUUID, String orgId, Integer product) {
    InventoryHostFacts baseFacts = createBaseHost(orgId);
    baseFacts.setProducts(product.toString());
    baseFacts.setHypervisorUuid(hypervisorUUID);
    baseFacts.setGuestId(UUID.randomUUID().toString());
    baseFacts.setVirtual(true);
    return baseFacts;
  }

  public static InventoryHostFacts createRhsmHost(
      String orgId, List<Integer> products, String syspurposeRole, OffsetDateTime syncTimestamp) {
    return createRhsmHost(
        orgId,
        StringUtils.collectionToCommaDelimitedString(products),
        syspurposeRole,
        syncTimestamp);
  }

  public static InventoryHostFacts createRhsmHost(
      List<Integer> products, String syspurposeRole, OffsetDateTime syncTimestamp) {
    return createRhsmHost(
        "test_org",
        StringUtils.collectionToCommaDelimitedString(products),
        syspurposeRole,
        syncTimestamp);
  }

  public static InventoryHostFacts createRhsmHost(
      String orgId, String products, String syspurposeRole, OffsetDateTime syncTimeStamp) {
    return createRhsmHost(orgId, products, ServiceLevel.EMPTY, syspurposeRole, syncTimeStamp);
  }

  public static InventoryHostFacts createRhsmHost(
      String orgId,
      String products,
      ServiceLevel sla,
      String syspurposeRole,
      OffsetDateTime syncTimeStamp) {

    return createRhsmHost(orgId, products, sla, Usage.EMPTY, syspurposeRole, syncTimeStamp);
  }

  public static InventoryHostFacts createRhsmHost(
      String orgId,
      String products,
      ServiceLevel sla,
      Usage usage,
      String syspurposeRole,
      OffsetDateTime syncTimeStamp) {

    InventoryHostFacts baseFacts = createBaseHost(orgId);
    baseFacts.setProducts(products);
    baseFacts.setSyspurposeRole(syspurposeRole);
    baseFacts.setSyspurposeSla(sla.getValue());
    baseFacts.setSyspurposeUsage(usage.getValue());
    baseFacts.setSyncTimestamp(syncTimeStamp.toString());
    return baseFacts;
  }

  public static InventoryHostFacts createQpcHost(String qpcProducts, OffsetDateTime syncTimestamp) {
    InventoryHostFacts baseFacts = createBaseHost("test_org");
    baseFacts.setQpcProducts(qpcProducts);
    baseFacts.setSyncTimestamp(syncTimestamp.toString());
    return baseFacts;
  }

  public static InventoryHostFacts createSystemProfileHost(
      List<Integer> products,
      Integer coresPerSocket,
      Integer sockets,
      OffsetDateTime syncTimestamp) {
    return createSystemProfileHost("test-org", products, coresPerSocket, sockets, syncTimestamp);
  }

  public static InventoryHostFacts createSystemProfileHost(
      String orgId,
      List<Integer> productIds,
      Integer coresPerSocket,
      Integer sockets,
      OffsetDateTime syncTimestamp) {
    InventoryHostFacts baseFacts = createBaseHost(orgId);
    baseFacts.setSystemProfileProductIds(StringUtils.collectionToCommaDelimitedString(productIds));
    baseFacts.setSystemProfileCoresPerSocket(coresPerSocket);
    baseFacts.setSystemProfileSockets(sockets);
    baseFacts.setSyncTimestamp(syncTimestamp.toString());
    return baseFacts;
  }

  public static InventoryHostFacts createBaseHost(String orgId) {
    InventoryHostFacts baseFacts = new InventoryHostFacts();
    baseFacts.setInventoryId(UUID.randomUUID());
    baseFacts.setDisplayName("Test System");
    baseFacts.setOrgId(orgId);
    baseFacts.setSyncTimestamp(OffsetDateTime.now().toString());
    baseFacts.setSubscriptionManagerId(UUID.randomUUID().toString());
    baseFacts.setVirtual(false);
    return baseFacts;
  }
}
