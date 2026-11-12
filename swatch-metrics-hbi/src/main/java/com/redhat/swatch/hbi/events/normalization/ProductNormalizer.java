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
    this.productTags = new HashSet<>();
    this.productIds = new HashSet<>();

    applySystemProfileProducts(systemProfileFacts);
    applySatelliteRoleProducts(systemProfileFacts, satelliteFacts);

    if (!skipRhsmFacts) {
      applyRhsmProducts(systemProfileFacts, rhsmFacts);
    }

    applyQpcProducts(systemProfileFacts, qpcFacts);

    normalizeRhelVariants(this.productTags);
    SubscriptionDefinition.pruneIncludedProducts(this.productTags);
  }

  private void applySystemProfileProducts(SystemProfileFacts systemProfileFacts) {
    Set<String> systemProfileProductIds = systemProfileFacts.getProductIds();

    if (systemProfileProductIds == null) {
      return;
    }

    var lookupParams =
        ProductTagLookupParams.builder()
            .engIds(systemProfileProductIds)
            .metricIds(APPLICABLE_METRIC_IDS)
            .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
            .isPaygEligibleProduct(false)
            .build();

    this.productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    this.productIds.addAll(systemProfileProductIds);
  }

  private void applySatelliteRoleProducts(
      SystemProfileFacts systemProfileFacts, Optional<SatelliteFacts> satelliteFacts) {
    String satelliteRole = satelliteFacts.map(SatelliteFacts::getRole).orElse(null);
    if (satelliteRole == null) {
      return;
    }

    var lookupParams =
        ProductTagLookupParams.builder()
            .role(satelliteRole)
            .metricIds(APPLICABLE_METRIC_IDS)
            .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
            .isPaygEligibleProduct(false)
            .build();

    productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
  }

  private void applyRhsmProducts(
      SystemProfileFacts systemProfileFacts, Optional<RhsmFacts> rhsmFacts) {
    String systemPurposeRole = rhsmFacts.map(RhsmFacts::getSystemPurposeRole).orElse(null);
    Set<String> products = rhsmFacts.map(RhsmFacts::getProductIds).orElse(Set.of());

    if (systemPurposeRole == null && products.isEmpty()) {
      return;
    }

    var lookupParams =
        ProductTagLookupParams.builder()
            .engIds(products)
            .role(systemPurposeRole)
            .metricIds(APPLICABLE_METRIC_IDS)
            .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
            .isPaygEligibleProduct(false)
            .build();

    productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    productIds.addAll(products);
  }

  private void applyQpcProducts(
      SystemProfileFacts systemProfileFacts, Optional<QpcFacts> qpcFacts) {
    Set<String> tags = new HashSet<>();
    Set<String> qpcProducts = qpcFacts.map(QpcFacts::getProducts).orElse(null);
    if (qpcProducts == null || !qpcProducts.contains("RHEL")) {
      return;
    }

    boolean hasNoSystemProfileProduct =
        Optional.ofNullable(systemProfileFacts.getProductIds()).orElse(Set.of()).isEmpty();

    String arch = systemProfileFacts.getArch();
    if (arch != null && hasNoSystemProfileProduct) {
      switch (arch) {
        case "x86_64", "i686", "i386":
          tags.add("RHEL for x86");
          break;
        case "aarch64":
          tags.add("RHEL for ARM");
          break;
        case "ppc64le":
          tags.add("RHEL for IBM Power");
          break;
        default:
          break;
      }
    }
    tags.add("RHEL");
    this.productTags.addAll(tags);
  }

  private void normalizeRhelVariants(Set<String> products) {
    long variantCount = products.stream().filter(this::isRhelVariant).count();
    boolean hasRhel = products.contains("RHEL");
    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      products.add("RHEL Ungrouped");
    }
  }

  private boolean isRhelVariant(String product) {
    return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
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

    Set<String> productTags = new HashSet<>();
    Set<String> metricIds = Set.of(metricId);

    // Apply system profile products with specific metric
    Set<String> systemProfileProductIds = systemProfileFacts.getProductIds();
    if (systemProfileProductIds != null) {
      var lookupParams =
          ProductTagLookupParams.builder()
              .engIds(systemProfileProductIds)
              .metricIds(metricIds)
              .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
              .isPaygEligibleProduct(false)
              .build();
      productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    }

    // Apply satellite role products with specific metric
    String satelliteRole = satelliteFacts.map(SatelliteFacts::getRole).orElse(null);
    if (satelliteRole != null) {
      var lookupParams =
          ProductTagLookupParams.builder()
              .role(satelliteRole)
              .metricIds(metricIds)
              .is3rdPartyMigration(systemProfileFacts.getIs3rdPartyMigrated())
              .isPaygEligibleProduct(false)
              .build();
      productTags.addAll(SubscriptionDefinition.getAllProductTags(lookupParams));
    }

    // Apply RHSM products with specific metric
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
    Set<String> qpcTags = new HashSet<>();
    Set<String> qpcProducts = qpcFacts.map(QpcFacts::getProducts).orElse(null);
    if (qpcProducts != null && qpcProducts.contains("RHEL")) {
      boolean hasNoSystemProfileProduct =
          Optional.ofNullable(systemProfileFacts.getProductIds()).orElse(Set.of()).isEmpty();
      String arch = systemProfileFacts.getArch();
      if (arch != null && hasNoSystemProfileProduct) {
        switch (arch) {
          case "x86_64", "i686", "i386":
            qpcTags.add("RHEL for x86");
            break;
          case "aarch64":
            qpcTags.add("RHEL for ARM");
            break;
          case "ppc64le":
            qpcTags.add("RHEL for IBM Power");
            break;
          default:
            break;
        }
      }
      qpcTags.add("RHEL");
      productTags.addAll(qpcTags);
    }

    // Apply RHEL variant normalization
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

    return productTags;
  }
}
