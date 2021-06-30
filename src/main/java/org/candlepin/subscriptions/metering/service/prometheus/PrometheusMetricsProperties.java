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
package org.candlepin.subscriptions.metering.service.prometheus;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.files.TagMetaData;
import org.candlepin.subscriptions.files.TagMetric;
import org.candlepin.subscriptions.files.TagProfile;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Properties related to all metrics that are to be gathered from the prometheus service. */
@Getter
@Setter
@ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.metric")
public class PrometheusMetricsProperties {

  private final TagProfile tagProfile;

  private Map<String, String> queryTemplates = new HashMap<>();

  private Map<String, String> accountQueryTemplates = new HashMap<>();

  /**
   * SPEL templates do not support nested expressions so the QueryBuilder will apply template
   * parameters a set number of times to prevent recursion.
   */
  private int templateParameterDepth = 3;

  private MetricProperties openshift = new MetricProperties();

  @Autowired
  public PrometheusMetricsProperties(TagProfile tagProfile) {
    this.tagProfile = tagProfile;
  }

  public Map<Uom, MetricProperties> getSupportedMetricsForProduct(String productTag) {
    if (!tagProfile.tagIsPrometheusEnabled(productTag)) {
      throw new UnsupportedOperationException(
          String.format("Metrics gathering for %s is not currently supported!", productTag));
    }

    Map<Uom, MetricProperties> metrics = new EnumMap<>(Uom.class);
    tagProfile.measurementsByTag(productTag).forEach(metric -> metrics.put(metric, openshift));
    return metrics;
  }

  public Integer getMetricsTimeoutForProductTag(String productTag) {
    // NOTE(khowell): doesn't make sense for a given product tag (e.g. OSD) to have different
    // metrics timeouts. Grabbing the first one for now.
    return getSupportedMetricsForProduct(productTag).values().stream()
        .map(MetricProperties::getQueryTimeout)
        .findFirst()
        .orElseThrow();
  }

  public Integer getRangeInMinutesForProductTag(String productTag) {
    return getSupportedMetricsForProduct(productTag).values().stream()
        .map(MetricProperties::getRangeInMinutes)
        .findFirst()
        .orElseThrow();
  }

  public Optional<String> getQueryTemplate(String templateKey) {
    return queryTemplates.containsKey(templateKey)
        ? Optional.of(queryTemplates.get(templateKey))
        : Optional.empty();
  }

  public Optional<String> getAccountQueryTemplate(String templateKey) {
    return accountQueryTemplates.containsKey(templateKey)
        ? Optional.of(accountQueryTemplates.get(templateKey))
        : Optional.empty();
  }

  public Optional<TagMetric> getTagMetric(String productTag, Uom metric) {
    if (!StringUtils.hasText(productTag) || Objects.isNull(metric)) {
      return Optional.empty();
    }

    return tagProfile.getTagMetrics().stream()
        .filter(x -> productTag.equals(x.getTag()) && metric.equals(x.getUom()))
        .findFirst();
  }

}
