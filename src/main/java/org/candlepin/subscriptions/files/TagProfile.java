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
package org.candlepin.subscriptions.files;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.springframework.util.StringUtils;

/** A base class for tag profiles. This class and its composites are loaded from a YAML profile. */
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@ToString
public class TagProfile {
  @Getter @Setter private List<TagMapping> tagMappings;
  @Getter @Setter private List<TagMetric> tagMetrics;
  @Getter @Setter private List<TagMetaData> tagMetaData;

  private Map<String, Set<String>> tagToEngProductsLookup;
  private Map<ProductUom, String> productUomToMetricIdLookup;
  @Getter private Set<String> tagsWithPrometheusEnabledLookup;
  private Map<String, Set<Uom>> measurementsByTagLookup;
  private Map<String, String> offeringProductNameToTagLookup;
  private Map<String, Granularity> finestGranularityLookup;
  private Map<String, TagMetaData> tagMetaDataToTagLookup;

  /** Initialize lookup fields */
  @PostConstruct
  public void initLookups() {
    tagToEngProductsLookup = new HashMap<>();
    productUomToMetricIdLookup = new HashMap<>();
    tagsWithPrometheusEnabledLookup = new HashSet<>();
    measurementsByTagLookup = new HashMap<>();
    offeringProductNameToTagLookup = new HashMap<>();
    tagMetaDataToTagLookup = new HashMap<>();
    finestGranularityLookup = new HashMap<>();
    tagMappings.forEach(this::handleTagMapping);
    tagMetrics.forEach(this::handleTagMetric);
    tagMetaData.forEach(this::handleTagMetaData);
  }

  private void handleTagMapping(TagMapping mapping) {
    if ("engId".equals(mapping.getValueType())) {
      mapping
          .getTags()
          .forEach(
              tag ->
                  tagToEngProductsLookup
                      .computeIfAbsent(tag, k -> new HashSet<>())
                      .add(mapping.getValue()));
    } else if ("productName".equals(mapping.getValueType())) {
      mapping.getTags().forEach(tag -> offeringProductNameToTagLookup.put(mapping.getValue(), tag));
    }
  }

  private void handleTagMetric(TagMetric tagMetric) {
    tagsWithPrometheusEnabledLookup.add(tagMetric.getTag());
    productUomToMetricIdLookup.put(
        new ProductUom(tagMetric.getTag(), tagMetric.getUom().value()), tagMetric.getMetricId());
    measurementsByTagLookup
        .computeIfAbsent(tagMetric.getTag(), k -> new HashSet<>())
        .add(tagMetric.getUom());
  }

  private void handleTagMetaData(TagMetaData tagMetaData) {
    tagMetaData
        .getTags()
        .forEach(tag -> {
          tagMetaDataToTagLookup.put(tag, tagMetaData);
          finestGranularityLookup.put(tag, tagMetaData.getFinestGranularity());
        });
  }

  public boolean tagSupportsEngProduct(String tag, String engId) {
    return tagToEngProductsLookup.getOrDefault(tag, Collections.emptySet()).contains(engId);
  }

  public boolean tagIsPrometheusEnabled(String tag) {
    return tagsWithPrometheusEnabledLookup.contains(tag);
  }

  public boolean tagSupportsGranularity(String tag, Granularity granularity) {
    return granularity.compareTo(finestGranularityLookup.get(tag)) < 1;
  }

  public String metricIdForTagAndUom(String tag, TallyMeasurement.Uom uom) {
    return productUomToMetricIdLookup.get(new ProductUom(tag, uom.value()));
  }

  public String tagForOfferingProductName(String offeringProductName) {
    return offeringProductNameToTagLookup.get(offeringProductName);
  }

  public Set<Uom> measurementsByTag(String tag) {
    return measurementsByTagLookup.getOrDefault(tag, new HashSet<>());
  }

  public Optional<TagMetaData> getTagMetaDataByTag(String productTag) {
    if (!StringUtils.hasText(productTag)) {
      return Optional.empty();
    }
    return Optional.ofNullable(tagMetaDataToTagLookup.get(productTag));
  }

}
