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
package org.candlepin.subscriptions.tally.facts;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductNormalizer {

  private static final Set<String> APPLICABLE_METRIC_IDS =
      Set.of(MetricIdUtils.getCores().toString(), MetricIdUtils.getSockets().toString());

  private static final boolean INCLUDE_PAYG_TAGS = false;

  private Set<String> getSystemProfileProducts(
      InventoryHostFacts hostFacts, boolean is3rdPartyMigrated) {
    Collection<String> systemProfileProductIds = hostFacts.getSystemProfileProductIds();
    if (systemProfileProductIds != null) {

      var lookupParams =
          ProductTagLookupParams.builder()
              .engIds(systemProfileProductIds)
              .metricIds(APPLICABLE_METRIC_IDS)
              .is3rdPartyMigration(is3rdPartyMigrated)
              .isPaygEligibleProduct(INCLUDE_PAYG_TAGS)
              .build();

      return SubscriptionDefinition.getAllProductTags(lookupParams);
    }
    return Set.of();
  }

  private Set<String> getSatelliteRoleProducts(
      InventoryHostFacts hostFacts, boolean is3rdPartyMigrated) {
    String satelliteRole = hostFacts.getSatelliteRole();
    if (satelliteRole != null) {

      var lookupParams =
          ProductTagLookupParams.builder()
              .role(satelliteRole)
              .metricIds(APPLICABLE_METRIC_IDS)
              .is3rdPartyMigration(is3rdPartyMigrated)
              .isPaygEligibleProduct(INCLUDE_PAYG_TAGS)
              .build();

      return SubscriptionDefinition.getAllProductTags(lookupParams);
    }
    return Set.of();
  }

  private Set<String> getRhsmProducts(InventoryHostFacts hostFacts, boolean is3rdPartyMigrated) {
    String syspurposeRole = hostFacts.getSyspurposeRole();
    Set<String> products = hostFacts.getProducts();

    var lookupParams =
        ProductTagLookupParams.builder()
            .engIds(products)
            .role(syspurposeRole)
            .metricIds(APPLICABLE_METRIC_IDS)
            .is3rdPartyMigration(is3rdPartyMigrated)
            .isPaygEligibleProduct(INCLUDE_PAYG_TAGS)
            .build();

    return SubscriptionDefinition.getAllProductTags(lookupParams);
  }

  private void addQpcProducts(Set<String> products, InventoryHostFacts hostFacts) {
    Set<String> qpcProducts = hostFacts.getQpcProducts();
    if (qpcProducts != null && qpcProducts.contains("RHEL")) {
      if (hostFacts.getSystemProfileArch() != null
          && CollectionUtils.isEmpty(hostFacts.getSystemProfileProductIds())) {
        switch (hostFacts.getSystemProfileArch()) {
          case "x86_64", "i686", "i386":
            products.add("RHEL for x86");
            break;
          case "aarch64":
            products.add("RHEL for ARM");
            break;
          case "ppc64le":
            products.add("RHEL for IBM Power");
            break;
          default:
            break;
        }
      }
      products.add("RHEL");
    }
  }

  private void normalizeRhelVariants(Set<String> products) {
    long variantCount = products.stream().filter(this::isRhelVariant).count();
    boolean hasRhel = products.contains("RHEL");
    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      products.add("RHEL Ungrouped");
    }
  }

  public Set<String> normalizeProducts(
      InventoryHostFacts hostFacts, boolean is3rdPartyMigrated, boolean skipRhsmFacts) {

    Set<String> productTags = new HashSet<>();

    productTags.addAll(getSystemProfileProducts(hostFacts, is3rdPartyMigrated));
    productTags.addAll(getSatelliteRoleProducts(hostFacts, is3rdPartyMigrated));

    if (!skipRhsmFacts) {
      productTags.addAll(getRhsmProducts(hostFacts, is3rdPartyMigrated));
    }

    addQpcProducts(productTags, hostFacts);
    normalizeRhelVariants(productTags);

    SubscriptionDefinition.pruneIncludedProducts(productTags);

    return productTags;
  }

  private boolean isRhelVariant(String product) {
    return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
  }
}
