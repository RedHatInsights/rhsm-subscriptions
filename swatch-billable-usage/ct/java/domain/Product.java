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
package domain;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public enum Product {
  ROSA("rosa", MetricIdUtils.getCores()),
  RHEL_PAYG_ADDON("rhel-for-x86-els-payg-addon", MetricIdUtils.getVCpus());

  private final ProductId id;
  private final Map<MetricId, Metric> metrics;

  Product(String name, MetricId... metricIds) {
    this.id = ProductId.fromString(name);
    this.metrics =
        Stream.of(metricIds)
            .collect(
                Collectors.toMap(
                    m -> m,
                    m ->
                        Variant.findByTag(this.id.getValue()).stream()
                            .map(v -> v.getSubscription().getMetric(m.getValue()).orElse(null))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElseThrow(
                                () -> new IllegalArgumentException("Metric not found: " + m))));
  }

  public String getName() {
    return this.id.getValue();
  }

  public Metric getMetric(MetricId metricId) {
    return this.metrics.get(metricId);
  }

  public double getBillingFactor(MetricId metricId) {
    return getMetric(metricId).getBillingFactor();
  }
}
