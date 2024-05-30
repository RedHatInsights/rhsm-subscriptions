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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.InventorySwatchDataCollator.Processor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySwatchDataCollatorTest {
  @Mock InventoryRepository inventoryRepository;

  @Mock HostRepository hostRepository;

  @Mock Processor processor;

  @Test
  void testCollatorDoesNothingWithNoData() {
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of());
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    verifyNoInteractions(processor);
    assertEquals(0, iterations);
  }

  @Test
  void testCollatorWorksWithOnlyHbiData() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    verify(processor).accept(hbiSystem, null, new OrgHostsData("placeholder"), 1);
    assertEquals(1, iterations);
  }

  @Test
  void testCollatorWorksWithOnlySwatchData() {
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of());

    Host swatchSystem = new Host();
    swatchSystem.setInventoryId("123e4567-e89b-12d3-a456-426614174000");
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of(swatchSystem));

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    verify(processor).accept(null, swatchSystem, new OrgHostsData("placeholder"), 1);
    assertEquals(1, iterations);
  }

  @Test
  void testCollatorProcessesSameInventoryIdTogetherInTwoIterations() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    hbiSystem.setInstanceId(hbiSystem.getInventoryId().toString());
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));

    Host first = new Host();
    first.setInventoryId("0");
    first.setInstanceId(first.getInstanceId());
    Host swatchSystem = new Host();
    swatchSystem.setInventoryId(hbiSystem.getInventoryId().toString());
    swatchSystem.setInstanceId(hbiSystem.getInventoryId().toString());
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of(swatchSystem, first));

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    collator.collateData("foo", 7, processor);

    verify(processor).accept(eq(hbiSystem), eq(swatchSystem), any(), eq(1));
    verify(processor).accept(eq(null), eq(first), any(), eq(2));
  }

  @Test
  void testCollatorProcessesHostsInSeveralIterations() {
    InventoryHostFacts first = new InventoryHostFacts();
    first.setInventoryId(UUID.randomUUID());
    first.setInstanceId("i1");

    InventoryHostFacts second = new InventoryHostFacts();
    second.setInventoryId(UUID.randomUUID());
    second.setInstanceId("i2");
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(first, second));

    Host swatchSystemForSecond = new Host();
    swatchSystemForSecond.setInventoryId(second.getInventoryId().toString());
    swatchSystemForSecond.setInstanceId(second.getInstanceId());

    Host swatchSystemOrphan = new Host();
    swatchSystemOrphan.setInventoryId(second.getInventoryId().toString());

    when(hostRepository.streamHbiHostsByOrgId(any()))
        .thenReturn(Stream.of(swatchSystemForSecond, swatchSystemOrphan));

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    collator.collateData("foo", 7, processor);

    verify(processor).accept(eq(first), isNull(), any(), eq(1));
    verify(processor).accept(eq(second), eq(swatchSystemForSecond), any(), eq(2));
    verify(processor).accept(isNull(), eq(swatchSystemOrphan), any(), eq(3));
  }

  @Test
  void testCollatorProcessesDifferentInventoryIdsInSeparateIterations() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));

    Host swatchSystem = new Host();
    swatchSystem.setInventoryId("223e4567-e89b-12d3-a456-426614174000");
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of(swatchSystem));

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    verify(processor).accept(hbiSystem, null, new OrgHostsData("placeholder"), 1);
    verify(processor).accept(null, swatchSystem, new OrgHostsData("placeholder"), 2);
    assertEquals(2, iterations);
  }

  @Test
  void testCollatorTracksPresentHypervisorUuidForGuest() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    hbiSystem.setHypervisorUuid("223e4567-e89b-12d3-a456-426614174000");
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));
    when(inventoryRepository.streamActiveSubscriptionManagerIds(any(), any()))
        .thenReturn(Stream.of("223e4567-e89b-12d3-a456-426614174000"));
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    ArgumentCaptor<OrgHostsData> orgHostsDataArgumentCaptor =
        ArgumentCaptor.forClass(OrgHostsData.class);
    verify(processor).accept(eq(hbiSystem), eq(null), orgHostsDataArgumentCaptor.capture(), eq(1));
    OrgHostsData orgHostsData = orgHostsDataArgumentCaptor.getValue();
    assertTrue(orgHostsData.hasHypervisorUuid("223e4567-e89b-12d3-a456-426614174000"));
    assertEquals(
        0,
        orgHostsData
            .hypervisorHostMap()
            .get("223e4567-e89b-12d3-a456-426614174000")
            .getNumOfGuests());
    assertEquals(1, iterations);
  }

  @Test
  void testCollatorTracksPresentHypervisorUuidViaSatelliteHypervisorUuidForGuest() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    hbiSystem.setSatelliteHypervisorUuid("223e4567-e89b-12d3-a456-426614174000");
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));
    when(inventoryRepository.streamActiveSubscriptionManagerIds(any(), any()))
        .thenReturn(Stream.of("223e4567-e89b-12d3-a456-426614174000"));
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    ArgumentCaptor<OrgHostsData> orgHostsDataArgumentCaptor =
        ArgumentCaptor.forClass(OrgHostsData.class);
    verify(processor).accept(eq(hbiSystem), eq(null), orgHostsDataArgumentCaptor.capture(), eq(1));
    OrgHostsData orgHostsData = orgHostsDataArgumentCaptor.getValue();
    assertTrue(orgHostsData.hasHypervisorUuid("223e4567-e89b-12d3-a456-426614174000"));
    assertEquals(
        0,
        orgHostsData
            .hypervisorHostMap()
            .get("223e4567-e89b-12d3-a456-426614174000")
            .getNumOfGuests());
    assertEquals(1, iterations);
  }

  @Test
  void testCollatorDoesNotTrackAbsentHypervisorUuidForGuest() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    hbiSystem.setHypervisorUuid("223e4567-e89b-12d3-a456-426614174000");
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));
    when(inventoryRepository.streamActiveSubscriptionManagerIds(any(), any()))
        .thenReturn(Stream.of("323e4567-e89b-12d3-a456-426614174000"));
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    ArgumentCaptor<OrgHostsData> orgHostsDataArgumentCaptor =
        ArgumentCaptor.forClass(OrgHostsData.class);
    verify(processor).accept(eq(hbiSystem), eq(null), orgHostsDataArgumentCaptor.capture(), eq(1));
    OrgHostsData orgHostsData = orgHostsDataArgumentCaptor.getValue();
    assertFalse(orgHostsData.hasHypervisorUuid("223e4567-e89b-12d3-a456-426614174000"));
    assertEquals(1, iterations);
  }

  @Test
  void testCollatorSkipsSubmanIdsNotNeeded() {
    InventoryHostFacts hbiSystem = new InventoryHostFacts();
    hbiSystem.setInventoryId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    hbiSystem.setHypervisorUuid("223e4567-e89b-12d3-a456-426614174000");
    when(inventoryRepository.streamFacts(any(), any())).thenReturn(Stream.of(hbiSystem));
    when(inventoryRepository.streamActiveSubscriptionManagerIds(any(), any()))
        .thenReturn(
            Stream.of(
                "123e4567-e89b-12d3-a456-426614174000",
                "123e4567-e89b-12d3-a456-426614174001",
                "223e4567-e89b-12d3-a456-426614174000"));
    when(hostRepository.streamHbiHostsByOrgId(any())).thenReturn(Stream.of());

    InventorySwatchDataCollator collator =
        new InventorySwatchDataCollator(inventoryRepository, hostRepository);
    var iterations = collator.collateData("foo", 7, processor);

    ArgumentCaptor<OrgHostsData> orgHostsDataArgumentCaptor =
        ArgumentCaptor.forClass(OrgHostsData.class);
    verify(processor).accept(eq(hbiSystem), eq(null), orgHostsDataArgumentCaptor.capture(), eq(1));
    OrgHostsData orgHostsData = orgHostsDataArgumentCaptor.getValue();
    assertTrue(orgHostsData.hasHypervisorUuid("223e4567-e89b-12d3-a456-426614174000"));
    assertEquals(
        0,
        orgHostsData
            .hypervisorHostMap()
            .get("223e4567-e89b-12d3-a456-426614174000")
            .getNumOfGuests());
    assertEquals(1, iterations);
  }
}
