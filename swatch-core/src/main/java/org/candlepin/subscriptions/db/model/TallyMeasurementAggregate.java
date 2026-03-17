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
 * DTO class representing aggregated tally measurement data grouped by snapshot date, measurement
 * type, and metric ID.
 *
 * <p>This class is used for queries that aggregate measurements with GROUP BY and SUM operations.
 *
 * <p>Class created with assistance from Claude Code
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TallyMeasurementAggregate implements Serializable {

  /** The snapshot date for this aggregated measurement */
  private OffsetDateTime snapshotDate;

  /** The measurement type (e.g., PHYSICAL, VIRTUAL, TOTAL) */
  private HardwareMeasurementType measurementType;

  /** The metric ID (e.g., Cores, Sockets, Instance-hours) */
  private String metricId;

  /** The aggregated value (typically a SUM of multiple measurements) */
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
        && measurementType == that.measurementType
        && Objects.equals(metricId, that.metricId)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotDate, measurementType, metricId, value);
  }
}
