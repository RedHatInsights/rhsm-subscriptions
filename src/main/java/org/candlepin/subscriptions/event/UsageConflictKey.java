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

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;

@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
public class UsageConflictKey {
  private String productTag;
  private String metricId;

  public static Set<UsageConflictKey> from(Event event) {
    return Sets.cartesianProduct(event.getProductTag(), new HashSet<>(event.getMeasurements()))
        .stream()
        .map(
            tuple ->
                new UsageConflictKey(
                    (String) tuple.get(0), getMetricId((Measurement) tuple.get(1))))
        .collect(Collectors.toSet());
  }

  public static String getMetricId(Measurement measurement) {
    return !StringUtils.isEmpty(measurement.getMetricId())
        ? measurement.getMetricId()
        : measurement.getUom();
  }
}
