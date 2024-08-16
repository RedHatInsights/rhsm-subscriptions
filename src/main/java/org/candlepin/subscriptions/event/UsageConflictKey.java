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
package org.candlepin.subscriptions.event;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.json.Measurement;

/**
 * A UsageConflictKey represents specific usage shared across multiple Events. Usage conflicts are
 * based on a product_tag and metric_id combination. This class is used by the {@link
 * UsageConflictTracker} to track conflicts for a specific host instance while determining which
 * Events should be amended. See {@link EventConflictResolver}.
 */
@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
public class UsageConflictKey {
  private String productTag;
  private String metricId;

  public static String getMetricId(Measurement measurement) {
    return !StringUtils.isEmpty(measurement.getMetricId())
        ? measurement.getMetricId()
        : measurement.getUom();
  }
}
