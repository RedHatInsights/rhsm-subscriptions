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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OfferingTest {

  @Test
  void verifySimpleGetSetTest() {
    final Set<String> skus = Set.of("childsku1", "childsku2");
    final Set<String> productIds = Set.of("1", "2");
    final Offering offering = new Offering();
    offering.setSku("testsku");
    offering.setChildSkus(skus);
    offering.setProductIds(productIds.stream().map(Integer::valueOf).collect(Collectors.toSet()));
    offering.setUsage(Usage.DEVELOPMENT_TEST);
    offering.setServiceLevel(ServiceLevel.PREMIUM);
    offering.setRole("testrole");
    offering.setCores(2);
    offering.setSockets(3);
    offering.setHypervisorCores(4);
    offering.setHypervisorSockets(5);
    offering.setProductFamily("testproductfamily");
    offering.setProductName("testproductname");
    assertEquals("testsku", offering.getSku());
    assertEquals(skus, offering.getChildSkus());
    assertEquals(productIds, offering.getProductIds());
    assertEquals(Usage.DEVELOPMENT_TEST, offering.getUsage());
    assertEquals(ServiceLevel.PREMIUM, offering.getServiceLevel());
    assertEquals("testrole", offering.getRole());
    assertEquals(2, offering.getCores());
    assertEquals(3, offering.getSockets());
    assertEquals(4, offering.getHypervisorCores());
    assertEquals(5, offering.getHypervisorSockets());
    assertEquals("testproductfamily", offering.getProductFamily());
    assertEquals("testproductname", offering.getProductName());
  }
}
