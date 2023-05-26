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

import static org.candlepin.subscriptions.tally.collector.Assertions.assertHardwareMeasurementTotals;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertNullExcept;
import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;

class UsageCalculationTest {

  @Test
  void testDefaults() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Test Product"));
    assertEquals("Test Product", calculation.getProductId());

    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      assertNull(calculation.getTotals(type), "Unexpected values for type: " + type);
    }
  }

  @Test
  void testAddToTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addToTotal(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL);
  }

  @Test
  void testPhysicalSystemTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addPhysical(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.PHYSICAL, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.PHYSICAL);
  }

  private UsageCalculation.Key createUsageKey(String product) {
    return new UsageCalculation.Key(
        product, ServiceLevel.EMPTY, Usage.EMPTY, BillingProvider.EMPTY, "_ANY");
  }

  @Test
  void testHypervisorTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addHypervisor(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.HYPERVISOR, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(
        calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.HYPERVISOR);
  }

  @Test
  void testVirtualTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addUnmappedGuest(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.VIRTUAL, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.VIRTUAL);
  }

  @Test
  void testAWSTotal() {
    checkCloudProvider(HardwareMeasurementType.AWS);
  }

  @Test
  void testAlibabaTotal() {
    checkCloudProvider(HardwareMeasurementType.ALIBABA);
  }

  @Test
  void testGoogleTotal() {
    checkCloudProvider(HardwareMeasurementType.GOOGLE);
  }

  @Test
  void testAzureTotal() {
    checkCloudProvider(HardwareMeasurementType.AZURE);
  }

  @Test
  void invalidCloudTypeThrowsExcpection() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          calculation.addCloudProvider(HardwareMeasurementType.VIRTUAL, 1, 1, 1);
        });
  }

  private void checkCloudProvider(HardwareMeasurementType providerType) {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4)
        .forEach(i -> calculation.addCloudProvider(providerType, i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, providerType, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL, providerType);
  }
}
