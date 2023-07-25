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
package com.redhat.swatch.configuration.registry;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.*;

/**
 * Variant is a mutually exclusive "edition" of a subscription, having the same "technical
 * fingerprint". Only humans familiar with the use case can distinguish between variants.
 * Operational model may also be a distinguishing attribute (e.g. hyperscaler - AWS, Azure, etc.).
 * Variants all have the same billing model.
 */
@Data
public class Variant {

  /** Convenience method to easily get the "parent" subscription for a Variant */
  @ToString.Exclude @EqualsAndHashCode.Exclude @NotNull private SubscriptionDefinition subscription;

  @NotNull @NotEmpty private String tag; // required
  private List<String> roles = new ArrayList<>();
  private List<String> engineeringIds = new ArrayList<>();
  private List<String> productNames = new ArrayList<>();

  public static Optional<Variant> findByRole(String role) {
    return SubscriptionDefinition.lookupSubscriptionByRole(role)
        .flatMap(
            subscription ->
                subscription.getVariants().stream()
                    .filter(
                        variant ->
                            !variant.getRoles().isEmpty() && variant.getRoles().contains(role))
                    .findFirst());
  }

  /**
   * Look up a variant by an engineering product id. Engineering product IDs can be found either in
   * a Subscription.Variant or Subscription.Fingerprint. In the event it matches a fingerprint,
   * return the Variant that's designated to be the default in the Subscription.defaults property
   *
   * @param engProductId
   * @return Optional<Variant>
   */
  public static Optional<Variant> findByEngProductId(String engProductId) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .flatMap(subscription -> subscription.getVariants().stream())
        .filter(variant -> variant.getEngineeringIds().contains(engProductId))
        .findFirst();
  }

  public static Optional<Variant> findByTag(String defaultVariantTag) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .flatMap(subscription -> subscription.getVariants().stream())
        .filter(variant -> Objects.equals(variant.getTag(), defaultVariantTag))
        .findFirst();
  }

  public static Optional<Variant> findByProductName(String productName) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getVariants)
        .flatMap(List::stream)
        .filter(v -> v.getProductNames().contains(productName))
        .findFirst();
  }
}
