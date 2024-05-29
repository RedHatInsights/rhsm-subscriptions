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
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductNormalizer {

  private Set<String> getSystemProfileProducts(
      InventoryHostFacts hostFacts, boolean isMetered, boolean is3rdPartyMigrated) {
    Collection<String> systemProfileProductIds = hostFacts.getSystemProfileProductIds();
    if (systemProfileProductIds != null) {
      return SubscriptionDefinition.getAllProductTagsByRoleOrEngIds(
          null, systemProfileProductIds, null, isMetered, is3rdPartyMigrated);
    }
    return Set.of();
  }

  private Set<String> getSatelliteRoleProducts(
      InventoryHostFacts hostFacts, boolean isMetered, boolean is3rdPartyMigrated) {
    String satelliteRole = hostFacts.getSatelliteRole();
    if (satelliteRole != null) {
      return SubscriptionDefinition.getAllProductTagsByRoleOrEngIds(
          satelliteRole, Set.of(), null, isMetered, is3rdPartyMigrated);
    }
    return Set.of();
  }

  private Set<String> getRhsmProducts(
      InventoryHostFacts hostFacts, boolean isMetered, boolean is3rdPartyMigrated) {
    String syspurposeRole = hostFacts.getSyspurposeRole();
    Set<String> products = hostFacts.getProducts();
    return SubscriptionDefinition.getAllProductTagsByRoleOrEngIds(
        syspurposeRole, products, null, isMetered, is3rdPartyMigrated);
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
    long variantCount = products.stream().filter(FactNormalizer::isRhelVariant).count();
    boolean hasRhel = products.contains("RHEL");
    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      products.add("RHEL Ungrouped");
    }
  }

  private void pruneExcludedProducts(Set<String> products) {
    Set<String> exclusions =
        products.stream()
            .map(SubscriptionDefinition::lookupSubscriptionByTag)
            .filter(Optional::isPresent)
            .flatMap(s -> s.get().getIncludedSubscriptions().stream())
            .flatMap(
                productId ->
                    SubscriptionDefinition.getAllProductTagsByProductId(productId).stream())
            .collect(Collectors.toSet());
    products.removeIf(exclusions::contains);
  }

  public Set<String> normalizeProducts(
      InventoryHostFacts hostFacts, boolean isMetered, boolean skipRhsmFacts) {

    Set<String> products = new HashSet<>();
    boolean is3rdPartyMigrated = false;

    products.addAll(getSystemProfileProducts(hostFacts, isMetered, is3rdPartyMigrated));
    products.addAll(getSatelliteRoleProducts(hostFacts, isMetered, is3rdPartyMigrated));

    if (!skipRhsmFacts) {
      products.addAll(getRhsmProducts(hostFacts, isMetered, is3rdPartyMigrated));
    }

    addQpcProducts(products, hostFacts);
    normalizeRhelVariants(products);
    pruneExcludedProducts(products);

    return products;
  }
}
