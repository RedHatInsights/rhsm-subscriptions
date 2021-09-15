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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jmx.JmxException;

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

    when(offeringSync.syncOffering(anyString())).thenReturn(SyncResult.FETCHED_AND_SYNCED);
    OfferingJmxBean subject = new OfferingJmxBean(offeringSync, capacityReconciliationController);

    // When syncing the offering,
    String actualMessage = subject.syncOffering(sku);

    // Then the offering was successfully fetched and synced, and the offering details are returned.
    verify(offeringSync).syncOffering(sku);
    String expectedMessage =
        "syncResult=FETCHED_AND_SYNCED (Successfully fetched and synced updated value from upstream) for offeringSku=\""
            + sku
            + "\".";
    assertEquals(expectedMessage, actualMessage);
  }

  @Test
  void testSyncOfferingWithServiceException() {
    // Given there are connection issues with the upstream product service,
    when(offeringSync.syncOffering(anyString()))
        .thenThrow(
            new ExternalServiceException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                "Unable to retrieve upstream offeringSku=\"BOGUS\"",
                new ApiException("Badness")));
    OfferingJmxBean subject = new OfferingJmxBean(offeringSync, capacityReconciliationController);

    // When syncing the offering, then a JmxException is thrown.
    assertThrows(JmxException.class, () -> subject.syncOffering("BOGUS"));
  }

  @Test
  void testSyncAllOfferings() {
    when(offeringSync.syncAllOfferings()).thenReturn(2);
    OfferingJmxBean subject = new OfferingJmxBean(offeringSync, capacityReconciliationController);

    // When requesting all offerings to be synced via the JMX bean interface,
    String message = subject.syncAllOfferings();

    // Then the offering sync controller's sync all method is called and a message of how many
    // offerings were enqueued is returned.
    assertEquals("Enqueued 2 offerings to be synced.", message);
  }
}
