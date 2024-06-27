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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.*;

/**
 * Variant is a mutually exclusive "edition" of a subscription, having the same "technical
 * fingerprint". Only humans familiar with the use case can distinguish between variants.
 * Operational model may also be a distinguishing attribute (e.g. hyperscaler - AWS, Azure, etc.).
 * Variants all have the same billing model.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Variant {

  /** Convenience method to easily get the "parent" subscription for a Variant */
  @ToString.Exclude @EqualsAndHashCode.Exclude @NotNull private SubscriptionDefinition subscription;

  @NotNull @NotEmpty private String tag; // required
  @Builder.Default private Boolean isMigrationProduct = false;
  @Builder.Default private Set<String> roles = new HashSet<>();
  @Builder.Default private Set<String> engineeringIds = new HashSet<>();
  @Builder.Default private Set<String> productNames = new HashSet<>();
  private String level2;

  public static Optional<Variant> findByTag(String defaultVariantTag) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .flatMap(subscription -> subscription.getVariants().stream())
        .filter(variant -> Objects.equals(variant.getTag(), defaultVariantTag))
        .findFirst();
  }

  public static boolean isGranularityCompatible(
      String productId, SubscriptionDefinitionGranularity granularity) {
    return findByTag(productId).stream()
        .map(Variant::getSubscription)
        .anyMatch(
            subscriptionDefinition ->
                subscriptionDefinition.getSupportedGranularity().contains(granularity));
  }

  protected static Stream<Variant> filterVariantsByProductName(
      String productName, boolean isMigrationProduct, boolean isMetered) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getVariants)
        .flatMap(List::stream)
        .filter(
            v ->
                v.getProductNames().contains(productName)
                    && Objects.equals(v.isMigrationProduct, isMigrationProduct)
                    && Objects.equals(v.subscription.isPaygEligible(), isMetered));
  }

  public static boolean isValidProductTag(String productId) {
    return findByTag(productId).isPresent();
  }

  public static List<Metric> getMetricsForTag(String tag) {
    return findByTag(tag).stream()
        .map(Variant::getSubscription)
        .map(SubscriptionDefinition::getMetrics)
        .flatMap(List::stream)
        .toList();
  }
}
