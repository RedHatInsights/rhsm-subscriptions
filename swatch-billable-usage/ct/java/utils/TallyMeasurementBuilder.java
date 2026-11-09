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
package utils;

import static domain.HardwareMeasurementType.PHYSICAL;

import domain.HardwareMeasurementType;
import org.candlepin.subscriptions.billable.usage.TallyMeasurement;

public final class TallyMeasurementBuilder {
  private TallyMeasurementBuilder() {}

  public static TallyMeasurement buildPhysicalMeasurement(String metricId, double value) {
    return buildPhysicalMeasurement(metricId, value, value);
  }

  public static TallyMeasurement buildPhysicalMeasurement(
      String metricId, double value, double currentTotal) {
    return buildMeasurement(metricId, PHYSICAL, value, currentTotal);
  }

  public static TallyMeasurement buildMeasurement(
      String metricId, HardwareMeasurementType type, double value, double currentTotal) {
    var measurement = new TallyMeasurement();
    measurement.setHardwareMeasurementType(type.name());
    measurement.setMetricId(metricId);
    measurement.setValue(value);
    measurement.setCurrentTotal(currentTotal);
    return measurement;
  }
}
