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
package org.candlepin.subscriptions.capacity;

import com.redhat.swatch.configuration.registry.Subscription;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.registry.TagProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Given a list of product IDs provided by a given product/subscription, returns the effective view
 * of products for capacity.
 */
@Component
public class CapacityProductExtractor {

  private static final Logger log = LoggerFactory.getLogger(CapacityProductExtractor.class);
  private final Map<Integer, Set<String>> engProductIdToSwatchProductIdsMap;

  public CapacityProductExtractor(TagProfile tagProfile) {
    this.engProductIdToSwatchProductIdsMap = tagProfile.getEngProductIdToSwatchProductIdsMap();
  }

  public Set<String> getProducts(Collection<String> productIds) {
    return mapEngProductsToSwatchIds(
        productIds.stream().map(CapacityProductExtractor::parseIntSkipUnparseable));
  }

  public Set<String> getProducts(Offering offering) {
    return mapEngProductsToSwatchIds(offering.getProductIds().stream());
  }

  Set<String> mapEngProductsToSwatchIds(Stream<Integer> productIds) {
    Set<String> products =
        productIds
            .filter(Objects::nonNull)
            .map(engProductIdToSwatchProductIdsMap::get)
            .filter(Objects::nonNull)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

    if (setIsInvalid(products)) {
      // Kick out the RHEL products since it's implicit with the RHEL-included product being there.
      products = products.stream().filter(matchesRhel().negate()).collect(Collectors.toSet());
    }
    return products;
  }

  private static Integer parseIntSkipUnparseable(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      log.debug("Skipping non-numeric product ID: {}", s);
    }
    return null;
  }

  /**
   * Return whether this set of products should be considered for capacity calculations.
   *
   * @param products a set of product names
   * @return true if the set is invalid for capacity calculations
   */
  public boolean setIsInvalid(Set<String> products) {

    return products.stream().anyMatch(matchesRhel())
        && products.stream().anyMatch(matchesRhelIncludedProduct());
  }

  private Predicate<String> matchesRhel() {
    return x -> x.startsWith("RHEL");
  }

  private Predicate<String> matchesRhelIncludedProduct() {
    return x -> x.startsWith("Satellite") || x.startsWith("OpenShift");
  }

  Set<String> getProductTagsFrom(Stream<Integer> engProductIds) {
    List<String> productIds = engProductIds.map(String::valueOf).toList();

    Set<String> ignoredSubscriptionIds =
        productIds.stream()
            .flatMap(id -> Subscription.lookupSubscriptionByEngId(id).stream())
            .flatMap(sub -> sub.getIncludedSubscriptions().stream())
            .collect(Collectors.toSet());

    Set<Variant> matches = new HashSet<>();

    for (String engProductId : productIds) {
      Variant.findByEngProductId(engProductId)
          .filter(variant -> !ignoredSubscriptionIds.contains(variant.getSubscription().getId()))
          .ifPresent(matches::add);

      Subscription subFoundByFingerprint =
          Subscription.lookupSubscriptionByEngId(engProductId).orElse(null);

      if (subFoundByFingerprint != null
          && !ignoredSubscriptionIds.contains(subFoundByFingerprint.getId())) {
        Variant.findByTag(subFoundByFingerprint.getDefaults().getVariant()).ifPresent(matches::add);
      }
    }

    return matches.stream().map(Variant::getTag).collect(Collectors.toSet());
  }
}
