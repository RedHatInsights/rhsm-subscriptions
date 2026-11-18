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
   * Core logic for determining product tags based on all ProductNormalizer rules. This orchestrates
   * the application of products from various sources (system profile, RHSM, satellite, QPC) and
   * applies normalization rules.
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

    applySystemProfileProducts(productTags, metricIds, systemProfileFacts);
    applySatelliteRoleProducts(productTags, metricIds, systemProfileFacts, satelliteFacts);
    if (!skipRhsmFacts) {
      applyRhsmProducts(productTags, metricIds, systemProfileFacts, rhsmFacts);
    }
    applyQpcProducts(productTags, systemProfileFacts, qpcFacts);
    normalizeRhelVariants(productTags);
    SubscriptionDefinition.pruneIncludedProducts(productTags);

    return productTags;
  }

  private static void applySystemProfileProducts(
      Set<String> productTags, Set<String> metricIds, SystemProfileFacts systemProfileFacts) {
    Set<String> systemProfileProductIds = systemProfileFacts.getProductIds();
    if (systemProfileProductIds == null || systemProfileProductIds.isEmpty()) {
      return;
    }

    var lookupParams =
        ProductTagLookupParams.builder()
            .engIds(systemProfileProductIds)
            .metricIds(metricIds)
            .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
            .isPaygEligibleProduct(false)
            .build();
    productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
  }

  private static void applySatelliteRoleProducts(
      Set<String> productTags,
      Set<String> metricIds,
      SystemProfileFacts systemProfileFacts,
      Optional<SatelliteFacts> satelliteFacts) {
    String satelliteRole = satelliteFacts.map(SatelliteFacts::getRole).orElse(null);
    if (satelliteRole == null || satelliteRole.isEmpty()) {
      return;
    }

    var lookupParams =
        ProductTagLookupParams.builder()
            .role(satelliteRole)
            .metricIds(metricIds)
            .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
            .isPaygEligibleProduct(false)
            .build();
    productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
  }

  private static void applyRhsmProducts(
      Set<String> productTags,
      Set<String> metricIds,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts) {
    String systemPurposeRole = rhsmFacts.map(RhsmFacts::getSystemPurposeRole).orElse(null);
    Set<String> rhsmProducts = rhsmFacts.map(RhsmFacts::getProductIds).orElse(Set.of());

    if (systemPurposeRole == null && rhsmProducts.isEmpty()) {
      return;
    }

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

  private static void applyQpcProducts(
      Set<String> productTags, SystemProfileFacts systemProfileFacts, Optional<QpcFacts> qpcFacts) {
    Set<String> qpcProducts = qpcFacts.map(QpcFacts::getProducts).orElse(null);
    if (qpcProducts == null || !qpcProducts.contains("RHEL")) {
      return;
    }

    boolean hasNoSystemProfileProduct =
        Optional.ofNullable(systemProfileFacts.getProductIds()).orElse(Set.of()).isEmpty();
    String arch = systemProfileFacts.getArch();

    if (arch != null && hasNoSystemProfileProduct) {
      String rhelVariant = mapArchitectureToRhelVariant(arch);
      if (rhelVariant != null) {
        productTags.add(rhelVariant);
      }
    }
    productTags.add("RHEL");
  }

  private static String mapArchitectureToRhelVariant(String arch) {
    return switch (arch) {
      case "x86_64", "i686", "i386" -> "RHEL for x86";
      case "aarch64" -> "RHEL for ARM";
      case "ppc64le" -> "RHEL for IBM Power";
      default -> null;
    };
  }

  private static void normalizeRhelVariants(Set<String> productTags) {
    long variantCount =
        productTags.stream()
            .filter(tag -> tag.startsWith("RHEL ") && !tag.startsWith("RHEL for "))
            .count();
    boolean hasRhel = productTags.contains("RHEL");

    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      productTags.add("RHEL Ungrouped");
    }
  }

  /**
   * Get product tags for a specific metric by applying all ProductNormalizer rules and filtering
   * out normalization artifacts. This is used when determining products per-measurement rather than
   * globally. Only returns real product variants that exist in the subscription definition registry
   * and support the specified metric.
   *
   * @param metricId Specific metric ID (e.g., "Cores" or "Sockets")
   * @param systemProfileFacts System profile facts from HBI
   * @param rhsmFacts RHSM facts (optional)
   * @param satelliteFacts Satellite facts (optional)
   * @param qpcFacts QPC facts (optional)
   * @param skipRhsmFacts Whether to skip RHSM facts
   * @return Set of real product variant tags that support this specific metric
   */
  public static Set<String> getProductTagsForMetric(
      String metricId,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<QpcFacts> qpcFacts,
      boolean skipRhsmFacts) {
    Set<String> allTags =
        collectProductTags(
            Set.of(metricId),
            systemProfileFacts,
            rhsmFacts,
            satelliteFacts,
            qpcFacts,
            skipRhsmFacts);

    // Filter to only include real product variants that support the metric
    // This removes normalization artifacts like "RHEL" and "RHEL Ungrouped"
    return allTags.stream()
        .filter(tag -> SubscriptionDefinition.variantSupportsMetric(tag, metricId))
        .collect(java.util.stream.Collectors.toSet());
  }
}
