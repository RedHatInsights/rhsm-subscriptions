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
package org.candlepin.subscriptions.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.springframework.util.ObjectUtils;
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
  @Getter private Set<String> serviceTypes;

  private Map<String, Set<String>> tagToEngProductsLookup;
  private Map<ProductUom, String> productUomToRhmMetricIdLookup;
  private Map<ProductUom, String> productUomToAwsDimensionLookup;
  @Getter private Set<String> tagsWithPrometheusEnabledLookup;
  private Map<String, Set<Uom>> measurementsByTagLookup;
  private Map<String, String> offeringProductNameToTagLookup;
  private Map<String, Set<String>> tagToOfferingProductNamesLookup;
  @Getter private Map<String, Set<String>> roleToTagLookup;
  @Getter private Map<String, Set<String>> archToTagLookup;
  private Map<String, Granularity> finestGranularityLookup;
  private Map<String, TagMetaData> tagMetaDataToTagLookup;
  private Map<String, List<Measurement.Uom>> tagToUomLookup;

  /** Initialize lookup fields */
  @PostConstruct
  public void initLookups() {
    tagToEngProductsLookup = new HashMap<>();
    productUomToRhmMetricIdLookup = new HashMap<>();
    productUomToAwsDimensionLookup = new HashMap<>();
    tagsWithPrometheusEnabledLookup = new HashSet<>();
    measurementsByTagLookup = new HashMap<>();
    offeringProductNameToTagLookup = new HashMap<>();
    tagToOfferingProductNamesLookup = new HashMap<>();
    roleToTagLookup = new HashMap<>();
    archToTagLookup = new HashMap<>();
    tagMetaDataToTagLookup = new HashMap<>();
    finestGranularityLookup = new HashMap<>();
    tagToUomLookup = new HashMap<>();
    serviceTypes = new HashSet<>();
    tagMappings.forEach(this::handleTagMapping);
    tagMetrics.forEach(this::handleTagMetric);
    tagMetaData.forEach(this::handleTagMetaData);

    // Validation needs to be performed once all data is loaded.
    validateContractEnabledBillingWindow();
  }

  private void validateContractEnabledBillingWindow() {
    Set<String> invalidTags =
        tagMetrics.stream()
            .filter(
                metric ->
                    isTagContractEnabled(metric.getTag())
                        && !BillingWindow.MONTHLY.equals(metric.getBillingWindow()))
            .map(TagMetric::getTag)
            .collect(Collectors.toSet());
    if (!invalidTags.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Contract enabled tags must be configured with MONTHLY billing window: %s",
              invalidTags));
    }
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
      mapping
          .getTags()
          .forEach(
              tag -> {
                offeringProductNameToTagLookup.put(mapping.getValue(), tag);
                tagToOfferingProductNamesLookup
                    .computeIfAbsent(tag, k -> new HashSet<>())
                    .add(mapping.getValue());
              });
    } else if ("role".equals(mapping.getValueType())) {
      roleToTagLookup
          .computeIfAbsent(mapping.getValue(), k -> new HashSet<>())
          .addAll(mapping.getTags());
    } else if ("arch".equals(mapping.getValueType())) {
      archToTagLookup
          .computeIfAbsent(mapping.getValue(), k -> new HashSet<>())
          .addAll(mapping.getTags());
    }
  }

  private void handleTagMetric(TagMetric tagMetric) {
    var prometheusEnabled =
        !ObjectUtils.isEmpty(tagMetric.getQueryParams())
            && tagMetric.getQueryParams().containsKey("prometheusMetric");
    if (prometheusEnabled) {
      tagsWithPrometheusEnabledLookup.add(tagMetric.getTag());
    }

    productUomToRhmMetricIdLookup.put(
        new ProductUom(tagMetric.getTag(), tagMetric.getUom().value()), tagMetric.getRhmMetricId());

    if (!ObjectUtils.isEmpty(tagMetric.getAwsDimension())) {
      productUomToAwsDimensionLookup.put(
          new ProductUom(tagMetric.getTag(), tagMetric.getUom().value()),
          tagMetric.getAwsDimension());
    }

    measurementsByTagLookup
        .computeIfAbsent(tagMetric.getTag(), k -> new HashSet<>())
        .add(tagMetric.getUom());
    List<Uom> uomList = tagToUomLookup.computeIfAbsent(tagMetric.getTag(), k -> new ArrayList<>());
    uomList.add(tagMetric.getUom());
  }

  private void handleTagMetaData(TagMetaData tagMetaData) {
    if (StringUtils.hasText(tagMetaData.getServiceType())) {
      serviceTypes.add(tagMetaData.getServiceType());
    }
    validateTagMetaData(tagMetaData);
    tagMetaData
        .getTags()
        .forEach(
            tag -> {
              tagMetaDataToTagLookup.put(tag, tagMetaData);
              finestGranularityLookup.put(tag, tagMetaData.getFinestGranularity());
            });
  }

  private void validateTagMetaData(TagMetaData tagMetaData) {
    if (tagMetaData.isContractEnabled() && !"PAYG".equals(tagMetaData.getBillingModel())) {
      throw new IllegalStateException(
          String.format(
              "A tag can only be configured as contractEnabled if billingModel=PAYG. %s",
              tagMetaData));
    }
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

  public String rhmMetricIdForTagAndUom(String tag, TallyMeasurement.Uom uom) {
    return productUomToRhmMetricIdLookup.get(new ProductUom(tag, uom.value()));
  }

  public String awsDimensionForTagAndUom(String tag, TallyMeasurement.Uom uom) {
    return productUomToAwsDimensionLookup.get(new ProductUom(tag, uom.value()));
  }

  public String tagForOfferingProductName(String offeringProductName) {
    return offeringProductNameToTagLookup.get(offeringProductName);
  }

  public Granularity granularityByTag(String productId) {
    return finestGranularityLookup.getOrDefault(productId, Granularity.DAILY);
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

  public List<Measurement.Uom> uomsForTag(String tag) {
    return tagToUomLookup.getOrDefault(tag, Collections.emptyList());
  }

  public Optional<TagMetric> getTagMetric(String productTag, Uom metric) {
    if (!StringUtils.hasText(productTag) || Objects.isNull(metric)) {
      return Optional.empty();
    }

    List<TagMetric> matchedMetrics =
        getTagMetrics().stream()
            .filter(x -> productTag.equals(x.getTag()) && metric.equals(x.getUom()))
            .collect(Collectors.toList());

    if (matchedMetrics.size() > 1) {
      throw new IllegalStateException(
          String.format("Duplicate tag metric found: %s/%s", productTag, metric));
    }
    // Optional.empty if list is empty.
    return matchedMetrics.stream().findFirst();
  }

  public Set<Uom> getSupportedMetricsForProduct(String productTag) {
    if (!tagIsPrometheusEnabled(productTag)) {
      throw new UnsupportedOperationException(
          String.format("Metrics gathering for %s is not currently supported!", productTag));
    }
    return measurementsByTag(productTag);
  }

  public Set<String> getTagsByRole(Role role) {
    if (Objects.isNull(role)) {
      return Collections.emptySet();
    }
    return roleToTagLookup.getOrDefault(role.value(), Collections.emptySet());
  }

  public Set<String> getTagsByEngProduct(String engProduct) {
    return tagToEngProductsLookup.entrySet().stream()
        .filter(e -> e.getValue().contains(engProduct))
        .map(Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Find the first instance of tag metadata that matches the specified service type.
   *
   * @param serviceType the service type to match.
   * @return Optional service type string.
   */
  public Optional<TagMetaData> getTagMetaDataByServiceType(String serviceType) {
    if (!StringUtils.hasText(serviceType)) {
      return Optional.empty();
    }
    return getTagMetaData().stream()
        .filter(meta -> serviceType.equals(meta.getServiceType()))
        .findFirst();
  }

  public Set<String> getTagsForServiceType(String serviceType) {
    if (!StringUtils.hasText(serviceType)) {
      return Collections.emptySet();
    }
    Set<String> tags = new HashSet<>();
    getTagMetaData().stream()
        .filter(meta -> serviceType.equals(meta.getServiceType()))
        .map(TagMetaData::getTags)
        .forEach(tags::addAll);
    return tags;
  }

  public Set<String> getOfferingProductNamesForTag(String productTag) {
    return tagToOfferingProductNamesLookup.getOrDefault(productTag, Collections.emptySet());
  }

  /**
   * Verify that the granularity requested is compatible with the finest granularity supported by
   * the product. For example, if the requester asks for HOURLY granularity but the product only
   * supports DAILY granularity, we can't meaningfully fulfill that request.
   *
   * @throws IllegalStateException if the granularities are not compatible
   */
  public void validateGranularityCompatibility(
      ProductId productId, Granularity requestedGranularity) {
    if (!tagSupportsGranularity(productId.toString(), requestedGranularity)) {
      String msg =
          String.format(
              "%s does not support any granularity finer than %s",
              productId, requestedGranularity.getValue());
      throw new IllegalStateException(msg);
    }
  }

  public Map<Integer, Set<String>> getEngProductIdToSwatchProductIdsMap() {
    Map<Integer, Set<String>> engProductIdToSwatchProductIdsMap = new HashMap<>();
    for (TagMapping tag : tagMappings) {
      if (!"engId".equals(tag.getValueType())) continue;

      Integer engId = Integer.parseInt(tag.getValue());

      if (engProductIdToSwatchProductIdsMap.containsKey(engId)) {
        throw new IllegalStateException("Duplicate engineering product ID found: " + engId);
      }
      engProductIdToSwatchProductIdsMap.put(engId, tag.getTags());
    }
    return engProductIdToSwatchProductIdsMap;
  }

  /**
   * Determine if a given product Id is PAYG Eligible based on the billing model from the tag
   * profile registry
   *
   * @param productId
   * @return boolean
   */
  public boolean isProductPAYGEligible(String productId) {

    var tagMeta = this.getTagMetaDataByTag(productId);
    if (tagMeta.isPresent()) {
      var billingModel = tagMeta.get().getBillingModel();
      return StringUtils.hasText(billingModel) && "PAYG".equalsIgnoreCase(billingModel);
    }

    return false;
  }

  public boolean isTagContractEnabled(String productTag) {
    var tagMeta = this.getTagMetaDataByTag(productTag);
    if (tagMeta.isPresent()) {
      String billingModel = tagMeta.get().getBillingModel();
      return tagMeta.get().isContractEnabled() && "PAYG".equalsIgnoreCase(billingModel);
    }
    return false;
  }
}
