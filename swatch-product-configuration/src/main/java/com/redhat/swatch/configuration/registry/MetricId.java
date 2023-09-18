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
package com.redhat.swatch.configuration.registry;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
// constructor is private so that the factory method is the only way to get a MetricId
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MetricId {

  private final String value;

  /**
   * Creates a MetricId from a String.
   *
   * @param value String value for the metric
   * @return a validated MetricId
   * @throws IllegalArgumentException if the metric is not defined in configuration
   */
  public static MetricId fromString(String value) {
    // NOTE: if the volume of data becomes large enough, we can pre-cache these values.
    String formattedValue = value.replace('_', '-');
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getMetrics)
        .flatMap(Collection::stream)
        .map(Metric::getId)
        .filter(metricId -> metricId.equalsIgnoreCase(formattedValue))
        .map(MetricId::new)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("MetricId: %s not found in configuration", value)));
  }

  public static Set<MetricId> getAll() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getMetrics)
        .flatMap(Collection::stream)
        .map(Metric::getId)
        .map(MetricId::new)
        .collect(Collectors.toSet());
  }

  // NOTE: intentionally overriding the toString() from @Data, so users can use getValue() and
  // toString() interchangeably without introducing errors
  public String toString() {
    return getValue();
  }

  public String toUpperCaseFormatted() {
    return getValue().toUpperCase().replace("-", "_");
  }
}
