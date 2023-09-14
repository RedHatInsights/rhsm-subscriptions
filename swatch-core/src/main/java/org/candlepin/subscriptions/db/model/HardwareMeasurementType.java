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

import java.util.Arrays;
import java.util.List;

/** Enum to capture the various types of measurements in the hardware_measurements table */
public enum HardwareMeasurementType {
  PHYSICAL,
  HYPERVISOR,
  VIRTUAL,
  TOTAL,
  AWS, // AWS measured by HBI data
  // NOTE: AWS_CLOUDIGRADE kept to avoid a bug when accessing old data created with now-retired
  // integration
  @Deprecated
  AWS_CLOUDIGRADE,
  GOOGLE("GCP"),
  ALIBABA,
  AZURE,
  EMPTY;

  private final String[] aliases;

  HardwareMeasurementType() {
    this.aliases = new String[0];
  }

  HardwareMeasurementType(String... aliases) {
    this.aliases = aliases;
  }

  public static boolean isSupportedCloudProvider(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }

    return getCloudProviderTypes().contains(fromString(name));
  }

  public static List<HardwareMeasurementType> getCloudProviderTypes() {
    return Arrays.asList(AWS, GOOGLE, AZURE, ALIBABA);
  }

  public static HardwareMeasurementType fromString(String name) {
    for (HardwareMeasurementType measurementType : HardwareMeasurementType.values()) {
      if (measurementType.name().equalsIgnoreCase(name)) {
        return measurementType;
      }

      for (String alias : measurementType.aliases) {
        if (alias.equalsIgnoreCase(name)) {
          return measurementType;
        }
      }
    }

    return null;
  }
}
