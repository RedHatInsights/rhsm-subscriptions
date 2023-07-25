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

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Given a list of product IDs provided by a given product/subscription, returns the effective view
 * of products for capacity.
 */
@Component
public class CapacityProductExtractor {

  public Set<String> getProducts(Collection<String> engProductIds) {

    Set<String> ignoredSubscriptionIds =
        engProductIds.stream()
            .flatMap(id -> SubscriptionDefinition.lookupSubscriptionByEngId(id).stream())
            .flatMap(sub -> sub.getIncludedSubscriptions().stream())
            .collect(Collectors.toSet());

    Set<Variant> matches = new HashSet<>();

    for (String engProductId : engProductIds) {
      Variant.findByEngProductId(engProductId)
          .filter(variant -> !ignoredSubscriptionIds.contains(variant.getSubscription().getId()))
          .ifPresent(matches::add);
    }

    return matches.stream().map(Variant::getTag).collect(Collectors.toSet());
  }
}
