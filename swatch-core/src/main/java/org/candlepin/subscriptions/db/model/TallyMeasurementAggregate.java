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

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * DTO class representing summed tally measurement data grouped by snapshot date and metric ID.
 *
 * <p>This DTO does NOT include measurementType because the database query groups by (snapshotDate,
 * metricId) and sums across all measurement types that match the filter criteria. This allows
 * pagination to work correctly for multi-type categories like CLOUD.
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TallyMeasurementAggregate implements Serializable {

  /** The snapshot date for this summed measurement */
  private OffsetDateTime snapshotDate;

  /** The metric ID (e.g., Cores, Sockets, Instance-hours) */
  private String metricId;

  /** The summed value across all measurement types that matched the filter */
  private Double value;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TallyMeasurementAggregate)) {
      return false;
    }
    TallyMeasurementAggregate that = (TallyMeasurementAggregate) o;
    return Objects.equals(snapshotDate, that.snapshotDate)
        && Objects.equals(metricId, that.metricId)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotDate, metricId, value);
  }
}
