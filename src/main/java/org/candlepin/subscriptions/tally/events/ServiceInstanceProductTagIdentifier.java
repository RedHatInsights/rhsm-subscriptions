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
package org.candlepin.subscriptions.tally.events;

import com.google.common.base.Strings;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Event;

public class ServiceInstanceProductTagIdentifier {
  private final String role;
  private final Collection<?> productIds;
  private final boolean isMetered;
  private final boolean is3rdPartyMigration;

  public ServiceInstanceProductTagIdentifier(Event event, boolean isMetered) {
    role = event.getRole() != null ? event.getRole().toString() : null;
    productIds = event.getProductIds();
    this.isMetered = isMetered;
    is3rdPartyMigration = event.getConversion();
  }

  public ServiceInstanceProductTagIdentifier(
      InventoryHostFacts hostFacts, boolean isMetered, boolean is3rdPartyMigrated) {
    role = hostFacts.getSyspurposeRole();
    productIds = hostFacts.getProducts();
    this.isMetered = isMetered;
    is3rdPartyMigration = is3rdPartyMigrated;
  }

  public Set<String> fetchProductTagsByFilters() {
    Set<String> serviceInstanceProductTags = new HashSet<>();

    if (!Strings.isNullOrEmpty(role)) {
      serviceInstanceProductTags.addAll(
          Variant.findByRole(role, is3rdPartyMigration, isMetered).stream()
              .map(Variant::getTag)
              .collect(Collectors.toSet()));
    }

    if (Objects.nonNull(this.productIds) && !this.productIds.isEmpty()) {
      productIds.stream()
          .map(Objects::toString)
          .collect(Collectors.toSet())
          .forEach(
              engId -> {
                Set<Variant> matchingVariants =
                    Variant.findByEngProductId(engId, is3rdPartyMigration, isMetered);

                // Add the variant that the fingerprint matches
                Set<String> tags =
                    new HashSet<>(matchingVariants.stream().map(Variant::getTag).toList());

                // Add additional tags as defined by config (to force that RHEL for x86 which
                // wouldn't match otherwise because there that definition isn't metered)
                for (String additionalTag :
                    matchingVariants.stream()
                        .flatMap(variant -> variant.getAdditionalTags().stream())
                        .toList()) {

                  // Only force the tag if it has an engineering match id....this is so 204 doesn't
                  // incorrectly map to 204,479 (mabye we don't need this?)
                  Optional<Variant> maybeVariant = Variant.findByTag(additionalTag);
                  if (maybeVariant.isPresent()
                      && maybeVariant.get().getEngineeringIds().stream()
                          .anyMatch(productIds::contains)) {
                    tags.add(additionalTag);
                  }
                }

                serviceInstanceProductTags.addAll(tags);
              });
    }

    SubscriptionDefinition.pruneIncludedProducts(serviceInstanceProductTags);

    return serviceInstanceProductTags;
  }
}
