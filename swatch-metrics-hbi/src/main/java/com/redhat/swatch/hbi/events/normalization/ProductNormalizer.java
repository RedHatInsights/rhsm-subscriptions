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
package com.redhat.swatch.hbi.events.normalization;

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

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import com.redhat.swatch.hbi.events.normalization.facts.QpcFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class ProductNormalizer {

  private static final Set<String> APPLICABLE_METRIC_IDS =
      Set.of(MetricIdUtils.getCores().toString(), MetricIdUtils.getSockets().toString());

  private final Set<String> productTags;
  private final Set<String> productIds;

  public ProductNormalizer(
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<QpcFacts> qpcFacts,
      boolean skipRhsmFacts) {
    this.productTags =
        collectProductTags(
            APPLICABLE_METRIC_IDS,
            systemProfileFacts,
            rhsmFacts,
            satelliteFacts,
            qpcFacts,
            skipRhsmFacts);
    this.productIds = collectProductIds(systemProfileFacts, rhsmFacts, skipRhsmFacts);
  }

  /**
   * Collects product IDs from system profile and RHSM facts.
   *
   * @param systemProfileFacts System profile facts from HBI
   * @param rhsmFacts RHSM facts (optional)
   * @param skipRhsmFacts Whether to skip RHSM facts
   * @return Set of product IDs
   */
  private static Set<String> collectProductIds(
      SystemProfileFacts systemProfileFacts, Optional<RhsmFacts> rhsmFacts, boolean skipRhsmFacts) {
    Set<String> productIds = new HashSet<>();

    if (systemProfileFacts.getProductIds() != null) {
      productIds.addAll(systemProfileFacts.getProductIds());
    }

    if (!skipRhsmFacts) {
      rhsmFacts.map(RhsmFacts::getProductIds).ifPresent(productIds::addAll);
    }

    return productIds;
  }

  /**
   * Core logic for determining product tags based on all ProductNormalizer rules. This is shared by
   * both the constructor (for all applicable metrics) and getProductTagsForMetric (for a specific
   * metric).
   *
   * @param metricIds Set of metric IDs to filter products by
   * @param systemProfileFacts System profile facts from HBI
   * @param rhsmFacts RHSM facts (optional)
   * @param satelliteFacts Satellite facts (optional)
   * @param qpcFacts QPC facts (optional)
   * @param skipRhsmFacts Whether to skip RHSM facts
   * @return Set of product tags that support the specified metrics
   */
  private static Set<String> collectProductTags(
      Set<String> metricIds,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<QpcFacts> qpcFacts,
      boolean skipRhsmFacts) {

    Set<String> productTags = new HashSet<>();

    // Apply system profile products
    Set<String> systemProfileProductIds = systemProfileFacts.getProductIds();
    if (systemProfileProductIds != null && !systemProfileProductIds.isEmpty()) {
      var lookupParams =
          ProductTagLookupParams.builder()
              .engIds(systemProfileProductIds)
              .metricIds(metricIds)
              .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
              .isPaygEligibleProduct(false)
              .build();
      productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    }

    // Apply satellite role products
    String satelliteRole = satelliteFacts.map(SatelliteFacts::getRole).orElse(null);
    if (satelliteRole != null && !satelliteRole.isEmpty()) {
      var lookupParams =
          ProductTagLookupParams.builder()
              .role(satelliteRole)
              .metricIds(metricIds)
              .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
              .isPaygEligibleProduct(false)
              .build();
      productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    }

    // Apply RHSM products
    if (!skipRhsmFacts) {
      String systemPurposeRole = rhsmFacts.map(RhsmFacts::getSystemPurposeRole).orElse(null);
      Set<String> rhsmProducts = rhsmFacts.map(RhsmFacts::getProductIds).orElse(Set.of());
      if (systemPurposeRole != null || !rhsmProducts.isEmpty()) {
        var lookupParams =
            ProductTagLookupParams.builder()
                .engIds(rhsmProducts)
                .role(systemPurposeRole)
                .metricIds(metricIds)
                .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
                .isPaygEligibleProduct(false)
                .build();
        productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
      }
    }

    // Apply QPC products
    Set<String> qpcProducts = qpcFacts.map(QpcFacts::getProducts).orElse(null);
    if (qpcProducts != null && qpcProducts.contains("RHEL")) {
      boolean hasNoSystemProfileProduct =
          Optional.ofNullable(systemProfileFacts.getProductIds()).orElse(Set.of()).isEmpty();
      String arch = systemProfileFacts.getArch();
      if (arch != null && hasNoSystemProfileProduct) {
        switch (arch) {
          case "x86_64", "i686", "i386":
            productTags.add("RHEL for x86");
            break;
          case "aarch64":
            productTags.add("RHEL for ARM");
            break;
          case "ppc64le":
            productTags.add("RHEL for IBM Power");
            break;
          default:
            break;
        }
      }
      productTags.add("RHEL");
    }

    // Normalize RHEL variants
    long variantCount =
        productTags.stream()
            .filter(tag -> tag.startsWith("RHEL ") && !tag.startsWith("RHEL for "))
            .count();
    boolean hasRhel = productTags.contains("RHEL");
    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      productTags.add("RHEL Ungrouped");
    }

    // Prune included products
    SubscriptionDefinition.pruneIncludedProducts(productTags);

    // Filter to only include product tags that actually support the requested metrics
    // This removes normalization artifacts like "RHEL" and "RHEL Ungrouped" that aren't real
    // products
    productTags =
        productTags.stream()
            .filter(tag ->
                metricIds.stream()
                    .anyMatch(
                        metricId -> SubscriptionDefinition.variantSupportsMetric(tag, metricId)))
            .collect(java.util.stream.Collectors.toSet());

    return productTags;
  }

  /**
   * Get product tags for a specific metric by applying all ProductNormalizer rules. This is used
   * when determining products per-measurement rather than globally.
   *
   * @param metricId Specific metric ID (e.g., "Cores" or "Sockets")
   * @param systemProfileFacts System profile facts from HBI
   * @param rhsmFacts RHSM facts (optional)
   * @param satelliteFacts Satellite facts (optional)
   * @param qpcFacts QPC facts (optional)
   * @param skipRhsmFacts Whether to skip RHSM facts
   * @return Set of product tags that support this specific metric
   */
  public static Set<String> getProductTagsForMetric(
      String metricId,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<QpcFacts> qpcFacts,
      boolean skipRhsmFacts) {
    return collectProductTags(
        Set.of(metricId), systemProfileFacts, rhsmFacts, satelliteFacts, qpcFacts, skipRhsmFacts);
  }
}
