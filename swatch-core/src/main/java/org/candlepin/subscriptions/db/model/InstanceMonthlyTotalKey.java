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

import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Key for instance monthly totals */
@Data
@NoArgsConstructor
@Embeddable
public class InstanceMonthlyTotalKey implements Serializable {
  private static final DateTimeFormatter MONTH_ID_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM");

  /** month in YYYY-MM format */
  @Column(nullable = false) // ENT-4622 needed to avoid recreating collections
  private String month;

  @Column(name = "metric_id", nullable = false)
  private String metricId;

  public static String formatMonthId(OffsetDateTime reference) {
    return reference.format(MONTH_ID_FORMATTER);
  }

  public InstanceMonthlyTotalKey(String month, String metricId) {
    this.month = month;
    this.metricId = MetricIdUtils.toUpperCaseFormatted(metricId);
  }

  public InstanceMonthlyTotalKey(OffsetDateTime reference, String metricId) {
    this.month = formatMonthId(reference);
    this.metricId = MetricIdUtils.toUpperCaseFormatted(metricId);
  }
}
