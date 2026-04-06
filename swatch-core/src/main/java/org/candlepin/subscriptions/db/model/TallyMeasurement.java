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

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;

public interface TallyMeasurement {
  Map<ReportCategory, Set<HardwareMeasurementType>> CATEGORY_MAP =
      Map.of(
          ReportCategory.PHYSICAL, Set.of(HardwareMeasurementType.PHYSICAL),
          ReportCategory.VIRTUAL, Set.of(HardwareMeasurementType.VIRTUAL),
          ReportCategory.HYPERVISOR, Set.of(HardwareMeasurementType.HYPERVISOR),
          ReportCategory.CLOUD, new HashSet<>(HardwareMeasurementType.getCloudProviderTypes()));

  OffsetDateTime getSnapshotDate();

  Double getMeasurement(HardwareMeasurementType type, MetricId metricId);

  Double extractRawValue(MetricId metricId, ReportCategory category);

  default Set<HardwareMeasurementType> getContributingTypes(ReportCategory category) {
    Set<HardwareMeasurementType> contributingTypes;
    if (category == null) {
      contributingTypes = Set.of(HardwareMeasurementType.TOTAL);
    } else {
      contributingTypes = CATEGORY_MAP.get(category);
    }
    return contributingTypes;
  }
}
