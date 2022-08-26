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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Totals;

public class Assertions {

  public static void assertTotalsCalculation(
      UsageCalculation calc, int sockets, int cores, int instances) {
    assertHardwareMeasurementTotals(calc, HardwareMeasurementType.TOTAL, sockets, cores, instances);
  }

  public static void assertPhysicalTotalsCalculation(
      UsageCalculation calc, int physSockets, int physCores, int physInstances) {
    assertHardwareMeasurementTotals(
        calc, HardwareMeasurementType.PHYSICAL, physSockets, physCores, physInstances);
  }

  public static void assertHypervisorTotalsCalculation(
      UsageCalculation calc, int hypSockets, int hypCores, int hypInstances) {
    assertHardwareMeasurementTotals(
        calc, HardwareMeasurementType.VIRTUAL, hypSockets, hypCores, hypInstances);
  }

  public static void assertHardwareMeasurementTotals(
      UsageCalculation calc, HardwareMeasurementType type, int sockets, int cores, int instances) {
    Totals totals = calc.getTotals(type);
    assertNotNull(totals, "No totals found for " + type);

    assertEquals(cores, totals.getCores());
    assertEquals(sockets, totals.getSockets());
    assertEquals(instances, totals.getInstances());
  }

  public static void assertNullExcept(UsageCalculation calc, HardwareMeasurementType... types) {
    List<HardwareMeasurementType> notNull = Arrays.asList(types);
    for (HardwareMeasurementType type : HardwareMeasurementType.values()) {
      if (notNull.contains(type)) {
        continue;
      }
      assertNull(calc.getTotals(type), "Expected type to be null: " + type);
    }
  }

  private Assertions() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }
}
