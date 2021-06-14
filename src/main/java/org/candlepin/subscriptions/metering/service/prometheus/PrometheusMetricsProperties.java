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

  @Autowired TagProfile tagProfile;

  private Map<String, String> queryTemplates = new HashMap<>();

  /**
   * SPEL templates do not support nested expressions so the QueryBuilder will apply template
   * parameters a set number of times to prevent recursion.
   */
  private int templateParameterDepth = 3;

  private MetricProperties openshift = new MetricProperties();

  public Map<Uom, MetricProperties> getSupportedMetricsForProduct(String productProfileId) {
    if (!tagProfile.tagIsPrometheusEnabled(productProfileId)) {
      throw new UnsupportedOperationException(
          String.format("Metrics gathering for %s is not currently supported!", productProfileId));
    }

    Map<Uom, MetricProperties> metrics = new EnumMap<>(Uom.class);
    tagProfile
        .measurementsByTag(productProfileId)
        .forEach(metric -> metrics.put(metric, openshift));
    return metrics;
  }

  public Collection<String> getMetricsEnabledProductProfiles() {
    return tagProfile.getTagsWithPrometheusEnabledLookup();
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public String getEnabledAccountPromQLforProductProfile(String productProfileId) {
    // NOTE(khowell): doesn't make sense for a given product profile (e.g. OSD) to have different
    // queries per-metric. Grabbing the first non-empty one for now.
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getEnabledAccountPromQL)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElseThrow();
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Integer getMetricsTimeoutForProductProfile(String productProfileId) {
    // NOTE(khowell): doesn't make sense for a given product profile (e.g. OSD) to have different
    // metrics timeouts. Grabbing the first one for now.
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getQueryTimeout)
        .findFirst()
        .orElseThrow();
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Integer getRangeInMinutesForProductProfile(String productProfileId) {
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getRangeInMinutes)
        .findFirst()
        .orElseThrow();
  }

  public Optional<String> getQueryTemplate(String templateKey) {
    return queryTemplates.containsKey(templateKey)
        ? Optional.of(queryTemplates.get(templateKey))
        : Optional.empty();
  }

  public Optional<TagMetric> getTagMetric(String tag, Uom metric) {
    if (!StringUtils.hasText(tag) || Objects.isNull(metric)) {
      return Optional.empty();
    }

    return tagProfile.getTagMetrics().stream()
        .filter(x -> tag.equals(x.getTag()) && metric.equals(x.getUom()))
        .findFirst();
  }

  public Optional<TagMetaData> getTagMetadata(String tag) {
    if (!StringUtils.hasText(tag)) {
      return Optional.empty();
    }

    return tagProfile.getTagMetaData().stream()
        .filter(meta -> meta.getTags().contains(tag))
        .findFirst();
  }
}
