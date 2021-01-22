/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import static org.candlepin.subscriptions.tally.collector.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public class UsageCalculationTest {

  @Test
  public void testDefaults() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Test Product"));
    assertEquals("Test Product", calculation.getProductId());

    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      assertNull(calculation.getTotals(type), "Unexpected values for type: " + type);
    }
  }

  @Test
  public void testAddToTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addToTotal(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL);
  }

  @Test
  public void testPhysicalSystemTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addPhysical(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.PHYSICAL, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.PHYSICAL);
  }

  private UsageCalculation.Key createUsageKey(String product) {
    return new UsageCalculation.Key(product, ServiceLevel.EMPTY, Usage.EMPTY);
  }

  @Test
  public void testHypervisorTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4).forEach(i -> calculation.addHypervisor(i + 2, i + 1, i));

    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.VIRTUAL, 15, 20, 10);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 15, 20, 10);
    assertNullExcept(calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.VIRTUAL);
  }

  @Test
  public void testAWSTotal() {
    checkCloudProvider(HardwareMeasurementType.AWS);
  }

  @Test
  public void testAlibabaTotal() {
    checkCloudProvider(HardwareMeasurementType.ALIBABA);
  }

  @Test
  public void testGoogleTotal() {
    checkCloudProvider(HardwareMeasurementType.GOOGLE);
  }

  @Test
  public void testAzureTotal() {
    checkCloudProvider(HardwareMeasurementType.AZURE);
  }

  @Test
  void testAwsCloudigradeTotal() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));

    calculation.addCloudigrade(HardwareMeasurementType.AWS_CLOUDIGRADE, 20);

    assertHardwareMeasurementTotals(
        calculation, HardwareMeasurementType.AWS_CLOUDIGRADE, 20, 0, 20);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 20, 0, 20);
    assertNullExcept(
        calculation, HardwareMeasurementType.TOTAL, HardwareMeasurementType.AWS_CLOUDIGRADE);
  }

  @Test
  void testAwsWithHbiAndCloudigrade() {
    UsageCalculation calculation = new UsageCalculation(createUsageKey("Product"));
    IntStream.rangeClosed(0, 4)
        .forEach(
            i -> calculation.addCloudProvider(HardwareMeasurementType.AWS, i + 100, i + 100, i));

    calculation.addCloudigrade(HardwareMeasurementType.AWS_CLOUDIGRADE, 20);

    assertHardwareMeasurementTotals(
        calculation, HardwareMeasurementType.AWS_CLOUDIGRADE, 20, 0, 20);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.TOTAL, 20, 0, 20);
    assertHardwareMeasurementTotals(calculation, HardwareMeasurementType.AWS, 510, 510, 10);
    assertNullExcept(
        calculation,
        HardwareMeasurementType.TOTAL,
        HardwareMeasurementType.AWS_CLOUDIGRADE,
        HardwareMeasurementType.AWS);
  }

  @Test
  public void invalidCloudTypeThrowsExcpection() {
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
