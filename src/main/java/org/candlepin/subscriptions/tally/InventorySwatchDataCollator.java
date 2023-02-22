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

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Coordinates processing of HBI and swatch data in a streaming, collated fashion. */
@Slf4j
@Component
public class InventorySwatchDataCollator {
  public interface Processor {
    void accept(
        InventoryHostFacts hbiSystem,
        Host swatchSystem,
        OrgHostsData orgHostsData,
        int iterationCount);
  }

  private final InventoryRepository inventoryRepository;
  private final HostRepository hostRepository;

  @Autowired
  public InventorySwatchDataCollator(
      InventoryRepository inventoryRepository, HostRepository hostRepository) {
    this.inventoryRepository = inventoryRepository;
    this.hostRepository = hostRepository;
  }

  /**
   * Fetches HBI and swatch system data in a consistent order, collates the records, and invokes a
   * handler on them.
   *
   * <p>Two primary streams - HBI system records and swatch system records are collated.
   *
   * <p>A third supplemental stream is used to track the presence of hypervisor records, with this
   * stream being ordered in a compatible way with the HBI system record stream and swatch system
   * record stream. This allows evaluation of hypervisor presence to happen in a compatible
   * streaming technique as well.
   *
   * <p>The order of the streams is hardware_subman_uuid first, then hypervisor_uuid, then
   * inventory_id. The hardware_subman_uuid sort causes all the systems running on a hypervisor to
   * be processed in a group, hypervisor_uuid forces the hypervisor itself to be processed last in
   * the group, and inventory_id as a final sort term is used to ensure a consistent order.
   *
   * <p>Each iteration operates against a single inventory ID, calling the processor with:
   * <li>the HBI record if present
   * <li>the Swatch record if present
   * <li>information collected for the underlying hardware (in OrgHostsData). OrgHostsData is
   *     persistent across iterations for the same hypervisor.
   *
   * @param orgId orgId to operate on
   * @param culledOffsetDays number of days before a system is considered culled by HBI
   * @param processor delegate that implements the Processor functional interface, to be called for
   *     each iteration.
   * @return the number of unique inventory IDs processed.
   */
  public int collateData(String orgId, int culledOffsetDays, Processor processor) {
    Stream<InventoryHostFacts> inventorySystemStream =
        inventoryRepository.streamFacts(orgId, culledOffsetDays);
    Stream<String> activeSubmanIdStream =
        inventoryRepository.streamActiveSubscriptionManagerIds(orgId, culledOffsetDays);
    Stream<Host> swatchSystemStream = hostRepository.streamHbiHostsByOrgId(orgId);

    PeekingIterator<InventoryHostFacts> inventoryDataIterator =
        Iterators.peekingIterator(inventorySystemStream.iterator());
    PeekingIterator<Host> swatchDataIterator =
        Iterators.peekingIterator(swatchSystemStream.iterator());
    PeekingIterator<String> activeSubmanIdIterator =
        Iterators.peekingIterator(activeSubmanIdStream.iterator());

    OrgHostsData orgHostsData = new OrgHostsData("placeholder"); // orgId not used
    int iterationCount = 0;

    while (inventoryDataIterator.hasNext() || swatchDataIterator.hasNext()) {
      iterationCount++;
      InventoryHostFacts nextHbiSystem = peekOrNull(inventoryDataIterator);
      Host nextSwatchHost = peekOrNull(swatchDataIterator);

      String activeInventoryId = minInventoryId(nextHbiSystem, nextSwatchHost);

      // limit further operation to "active" records - those that should be processed in this
      // iteration
      InventoryHostFacts activeHbiSystem =
          Optional.ofNullable(nextHbiSystem)
              .filter(host -> Objects.equals(host.getInventoryId().toString(), activeInventoryId))
              .orElse(null);
      Host activeSwatchSystem =
          Optional.ofNullable(nextSwatchHost)
              .filter(host -> Objects.equals(host.getInventoryId(), activeInventoryId))
              .orElse(null);

      if (activeHbiSystem != null) {
        // consume the hbi system from the stream, to prepare for the next iteration
        inventoryDataIterator.next();
        Optional<String> hypervisorUuid =
            Stream.of(
                    activeHbiSystem.getHypervisorUuid(),
                    activeHbiSystem.getSatelliteHypervisorUuid())
                .filter(Objects::nonNull)
                .findFirst();
        if (hypervisorUuid.isPresent() && !orgHostsData.hasHypervisorUuid(hypervisorUuid.get())) {
          orgHostsData = trackActiveHypervisor(hypervisorUuid.get(), activeSubmanIdIterator);
        }
      }
      if (activeSwatchSystem != null) {
        // consume the swatch system from the stream to prepare for the next iteration
        swatchDataIterator.next();
      }
      processor.accept(activeHbiSystem, activeSwatchSystem, orgHostsData, iterationCount);
    }
    return iterationCount;
  }

  private <T> T peekOrNull(PeekingIterator<T> iterator) {
    return iterator.hasNext() ? iterator.peek() : null;
  }

  private String minInventoryId(InventoryHostFacts inventoryHost, Host swatchHost) {
    return Stream.of(
            Optional.ofNullable(inventoryHost)
                .map(InventoryHostFacts::getInventoryId)
                .map(UUID::toString)
                .orElse(null),
            Optional.ofNullable(swatchHost).map(Host::getInventoryId).orElse(null))
        .filter(Objects::nonNull)
        .min(String::compareTo)
        .orElseThrow();
  }

  /**
   * Return an instance of OrgHostsData that tracks the active hypervisor, iterating through the
   * stream of activeSubmanIds.
   *
   * <p>If the hypervisor UUID is present in HBI data, the returned OrgHostsData will have a
   * placeholder Host record for tracking buckets and guest counts, otherwise it will have no data.
   *
   * @param hypervisorUuid the hardware subman ID to start tracking
   * @param activeSubmanIds iterator of stream of present subman IDs
   */
  private OrgHostsData trackActiveHypervisor(
      String hypervisorUuid, PeekingIterator<String> activeSubmanIds) {
    log.debug("Active hypervisorUuid={}", hypervisorUuid);
    while (activeSubmanIds.hasNext() && activeSubmanIds.peek().compareTo(hypervisorUuid) < 0) {
      // discard any subman IDs less than the specified hypervisor UUID
      activeSubmanIds.next();
    }
    OrgHostsData orgHostsData = new OrgHostsData("placeholder"); // orgId not used
    if (activeSubmanIds.hasNext() && Objects.equals(activeSubmanIds.peek(), hypervisorUuid)) {
      Host placeholder = new Host();
      placeholder.setNumOfGuests(0);
      log.debug("Adding placeholder for hypervisorUuid={}", hypervisorUuid);
      orgHostsData.addHostToHypervisor(hypervisorUuid, placeholder);
      orgHostsData.addHypervisorFacts(hypervisorUuid, new NormalizedFacts());
      orgHostsData.addHostMapping(hypervisorUuid, hypervisorUuid);
    }
    return orgHostsData;
  }
}
