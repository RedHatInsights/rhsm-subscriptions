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
package com.redhat.swatch.billable.usage.services.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContractCoverage {
  private final String metricId;
  private final boolean gratis;
  private final double total;
  private final String selectedLicenseId;

  @Builder.Default
  private final SequencedMap<String, Double> capacityByLicense =
      Collections.unmodifiableSequencedMap(new LinkedHashMap<>());

  @Override
  public String toString() {
    return String.format(
        "Coverage for metric '%s': %s%s selectedLicenseId=%s capacityByLicense=%s",
        metricId, total, gratis ? "(Gratis)" : "", selectedLicenseId, capacityByLicense);
  }
}
