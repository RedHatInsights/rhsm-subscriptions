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
package org.candlepin.subscriptions.subscription;

import com.google.common.base.Strings;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.Offering;

public class OfferingProductTagIdentifier {
  private final String role;
  private final Collection<?> productIds;
  private final String productName;
  private final boolean isMetered;
  private final boolean is3rdPartyMigration;

  public OfferingProductTagIdentifier(Offering offering) {
    role = offering.getRole();
    productIds = offering.getProductIds();
    productName = offering.getProductName();
    isMetered = offering.isMetered();
    is3rdPartyMigration = offering.isMigrationOffering();
  }

  public Set<String> fetchProductTagsByFilters() {
    Set<String> offeringProductTags = new HashSet<>();

    if (!Strings.isNullOrEmpty(role)) {
      offeringProductTags.addAll(
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

                offeringProductTags.addAll(tags);
              });
    }
    // if not found, let's use the product name
    if ((offeringProductTags.isEmpty()) && !Strings.isNullOrEmpty(productName)) {
      offeringProductTags.addAll(
          Variant.filterVariantsByProductName(productName, is3rdPartyMigration, isMetered)
              .filter(v -> v.getSubscription().isPaygEligible() == isMetered)
              .map(Variant::getTag)
              .collect(Collectors.toSet()));
    }

    SubscriptionDefinition.pruneIncludedProducts(offeringProductTags);

    return offeringProductTags;
  }
}
