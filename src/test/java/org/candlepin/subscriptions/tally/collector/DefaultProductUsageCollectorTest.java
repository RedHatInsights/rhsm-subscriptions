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
package org.candlepin.subscriptions.tally.collector;

import static org.candlepin.subscriptions.tally.collector.Assertions.assertHardwareMeasurementTotals;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertNullExcept;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertPhysicalTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertVirtualTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.TestHelper.cloudMachineFacts;
import static org.candlepin.subscriptions.tally.collector.TestHelper.guestFacts;
import static org.candlepin.subscriptions.tally.collector.TestHelper.hypervisorFacts;
import static org.candlepin.subscriptions.tally.collector.TestHelper.physicalNonHypervisor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.List;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;

class DefaultProductUsageCollectorTest {

  private DefaultProductUsageCollector collector;

  public DefaultProductUsageCollectorTest() {
    collector = new DefaultProductUsageCollector();
  }

  @Test
  void testCountsForHypervisor() {
    // By default hypervisors are not tracked at all and therefor
    // it is considered to be a physical machine.
    NormalizedFacts facts = hypervisorFacts(4, 12);

    UsageCalculation calc = new UsageCalculation(createUsageKey());
    collector.collect(calc, facts);
    assertTotalsCalculation(calc, 4, 12, 1);
    assertPhysicalTotalsCalculation(calc, 4, 12, 1);
    assertNullExcept(calc, HardwareMeasurementType.TOTAL, HardwareMeasurementType.PHYSICAL);
  }

  @Test
  void testCountsForGuestWithUnknownHypervisor() {
    NormalizedFacts facts = guestFacts(3, 12, false);

    UsageCalculation calc = new UsageCalculation(createUsageKey());
    collector.collect(calc, facts);

    assertVirtualTotalsCalculation(calc, 3, 12, 1);
    assertTotalsCalculation(calc, 3, 12, 1);
    assertNullExcept(calc, HardwareMeasurementType.VIRTUAL, HardwareMeasurementType.TOTAL);
  }

  @Test
  void testCountsForGuestWithKnownHypervisor() {
    NormalizedFacts facts = guestFacts(3, 12, true);

    UsageCalculation calc = new UsageCalculation(createUsageKey());
    collector.collect(calc, facts);

    assertVirtualTotalsCalculation(calc, 3, 12, 1);
    assertTotalsCalculation(calc, 3, 12, 1);
    assertNullExcept(calc, HardwareMeasurementType.VIRTUAL, HardwareMeasurementType.TOTAL);
  }

  @Test
  void testCountsForPhysicalSystem() {
    NormalizedFacts facts = physicalNonHypervisor(4, 12);

    UsageCalculation calc = new UsageCalculation(createUsageKey());
    collector.collect(calc, facts);

    assertTotalsCalculation(calc, 4, 12, 1);
    assertPhysicalTotalsCalculation(calc, 4, 12, 1);
    assertNullExcept(calc, HardwareMeasurementType.TOTAL, HardwareMeasurementType.PHYSICAL);
  }

  @Test
  void testCountsForCloudProvider() {
    // Cloud provider host should contribute to the matched supported cloud provider,
    // as well as the overall total. A cloud host should only ever contribute 1 socket
    // along with its cores.
    NormalizedFacts facts = cloudMachineFacts(HardwareMeasurementType.AWS, 4, 12);

    UsageCalculation calc = new UsageCalculation(createUsageKey());
    collector.collect(calc, facts);

    assertTotalsCalculation(calc, 1, 12, 1);
    assertHardwareMeasurementTotals(calc, HardwareMeasurementType.AWS, 1, 12, 1);
    assertNullExcept(calc, HardwareMeasurementType.TOTAL, HardwareMeasurementType.AWS);
  }

  @Test
  void testCountsForMarketplaceInstances() {
    // Marketplace instance zeros should be ignored from the overall total
    List<NormalizedFacts> conditions = new LinkedList<>();
    NormalizedFacts marketFacts = cloudMachineFacts(HardwareMeasurementType.AWS, 1, 0);
    marketFacts.setMarketplace(true);
    conditions.add(marketFacts);

    NormalizedFacts physicalNonHypervisor = physicalNonHypervisor(1, 0);
    physicalNonHypervisor.setMarketplace(true);
    conditions.add(physicalNonHypervisor);

    NormalizedFacts virtual = guestFacts(5, 0, false);
    virtual.setMarketplace(true);
    conditions.add(virtual);

    UsageCalculation calc = new UsageCalculation(createUsageKey());

    for (NormalizedFacts current : conditions) {
      collector.collect(calc, current);
    }
    assertTotalsCalculation(calc, 0, 0, 3);
  }

  @Test
  void testBucketsHaveAsHypervisorFalse() {
    var facts = new NormalizedFacts();
    var bucket = collector.buildBucket(createUsageKey(), facts);
    assertTrue(bucket.isPresent());
    assertFalse(bucket.get().getKey().getAsHypervisor());
  }

  private UsageCalculation.Key createUsageKey() {
    return new UsageCalculation.Key(
        "NON_RHEL", ServiceLevel.EMPTY, Usage.EMPTY, BillingProvider.EMPTY, "_ANY");
  }
}
