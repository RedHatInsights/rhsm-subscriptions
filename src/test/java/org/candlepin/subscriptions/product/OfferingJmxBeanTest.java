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
package org.candlepin.subscriptions.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfferingJmxBeanTest {

  @Mock OfferingSyncController offeringSync;

  @Mock CapacityReconciliationController capacityReconciliationController;

  @Test
  void testSyncOffering() {
    // Given that an offering is fetchable upstream,
    var sku = "MW01485";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Set.of("SVCMW01485"));
    expected.setProductIds(
        Set.of(
            69, 70, 185, 194, 197, 201, 205, 240, 271, 290, 311, 317, 318, 326, 329, 408, 458, 473,
            479, 491, 518, 519, 546, 579, 588, 603, 604, 608, 610, 645));
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Container Platform");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    when(offeringSync.getUpstreamOffering(anyString())).thenReturn(Optional.of(expected));
    OfferingJmxBean subject = new OfferingJmxBean(offeringSync, capacityReconciliationController);

    // When syncing the offering,
    String actualMessage = subject.syncOffering(sku);

    // Then the offering was successfully fetched and synced, and the offering details are returned.
    verify(offeringSync).getUpstreamOffering(sku);
    verify(offeringSync).syncOffering(expected);
    assertTrue(
        actualMessage.contains("Offering(sku=MW01485"), "Offering should have synced successfully");
  }

  @Test
  void testSyncOfferingNoOffering() {
    // Given that an offering is either not allowlisted or not found upstream,
    var sku = "BOGUS";
    when(offeringSync.getUpstreamOffering(anyString())).thenReturn(Optional.empty());
    OfferingJmxBean subject = new OfferingJmxBean(offeringSync, capacityReconciliationController);

    // When syncing the offering,
    String actualMessage = subject.syncOffering(sku);

    // Then no attempt was made to sync the offering and an error message is returned.
    verify(offeringSync).getUpstreamOffering(sku);
    verify(offeringSync, never()).syncOffering(any());
    assertEquals(
        "{\"message\": \"offeringSku=\"" + sku + "\" was not found/allowlisted.\"}", actualMessage);
  }
}
