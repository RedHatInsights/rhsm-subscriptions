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

import com.google.common.collect.MoreCollectors;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Subscription is an offering with one or more variants. Defines a specific metering model. Has a
 * single technical fingerprint. Defines a set of metrics.
 */
@Data
@Slf4j
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionDefinition {
  public static final List<String> ORDERED_GRANULARITY =
      List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");

  /**
   * A family of solutions that is logically related, having one or more subscriptions distinguished
   * by unique technical fingerprints (e.g. different arches)
   */
  @NotNull @NotEmpty private String platform; // required

  @NotNull @NotEmpty private String id; // required

  /**
   * Enables capability to inherit billing model information from their parent subscription. Unused
   * prior to <a href="https://issues.redhat.com/browse/BIZ-629">BIZ-629</a>
   */
  private String parentSubscription;

  /**
   * defines an "in-the-box" subscription. Considered included from both usage and capacity
   * perspectives.
   */
  @Builder.Default private List<String> includedSubscriptions = new ArrayList<>();

  @Builder.Default private List<Variant> variants = new ArrayList<>();
  private String serviceType;
  @Builder.Default private List<Metric> metrics = new ArrayList<>();
  private Defaults defaults;
  private boolean contractEnabled;
  private boolean vdcType;

  /**
   * @param serviceType
   * @return Optional<Subscription>
   */
  public static List<SubscriptionDefinition> findByServiceType(String serviceType) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getServiceType(), serviceType))
        .toList();
  }

  public List<String> getMetricIds() {
    return this.getMetrics().stream()
        .map(Metric::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Optional<Metric> getMetric(String metricId) {
    return this.getMetrics().stream()
        .filter(x -> Objects.equals(x.getId(), metricId))
        .collect(MoreCollectors.toOptional());
  }

  public static Optional<SubscriptionDefinition> findById(String id) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getId(), id))
        .collect(MoreCollectors.toOptional());
  }

  /**
   * @return List<String> serviceTypes
   */
  public static Set<String> getAllServiceTypes() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getServiceType)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public static boolean isPrometheusEnabled(@NotNull @NotEmpty String tag) {
    return lookupSubscriptionByTag(tag).filter(SubscriptionDefinition::isPrometheusEnabled).stream()
        .findFirst()
        .isPresent();
  }

  public boolean isPrometheusEnabled() {
    return this.getMetrics().stream().anyMatch(metric -> Objects.nonNull(metric.getPrometheus()));
  }

  public SubscriptionDefinitionGranularity getFinestGranularity() {

    return this.isPrometheusEnabled()
        ? SubscriptionDefinitionGranularity.HOURLY
        : SubscriptionDefinitionGranularity.DAILY;
  }

  public List<SubscriptionDefinitionGranularity> getSupportedGranularity() {
    List<SubscriptionDefinitionGranularity> granularity =
        new ArrayList<>(List.of(SubscriptionDefinitionGranularity.values()));

    if (!isPrometheusEnabled()) {
      granularity.remove(SubscriptionDefinitionGranularity.HOURLY);
    }

    return granularity;
  }

  public static boolean supportsGranularity(SubscriptionDefinition sub, String granularity) {
    return sub.getSupportedGranularity().stream()
        .map(x -> x.toString().toLowerCase())
        .toList()
        .contains(granularity.toLowerCase());
  }

  public static boolean variantSupportsGranularity(String tag, String granularity) {
    return lookupSubscriptionByTag(tag)
        .map(subscription -> supportsGranularity(subscription, granularity))
        .orElseGet(
            () -> {
              log.warn("Granularity requested for missing subscription variant: {}", tag);
              return false;
            });
  }

  /**
   * @param isPaygEligible
   * @return Set<String> variant tags
   */
  public static Set<String> getAllTags(boolean isPaygEligible) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscriptionDefinition -> subscriptionDefinition.isPaygEligible() == isPaygEligible)
        .map(SubscriptionDefinition::getVariants)
        .flatMap(Collection::stream)
        .map(Variant::getTag)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static Set<String> getAllNonPaygTags() {
    return getAllTags(false);
  }

  public static Set<String> getAllProductTagsByProductId(String id) {
    return SubscriptionDefinition.findById(id)
        .map(s -> s.getVariants().stream().map(Variant::getTag).collect(Collectors.toSet()))
        .orElse(Set.of());
  }

  public static Set<String> getAllProductTagsByRoleOrEngIds(
      String role,
      Collection<?> engIds,
      String productName,
      boolean isMetered,
      boolean is3rdPartyMigration) {
    Set<String> productTags = new HashSet<>();
    if (!isNullOrEmpty(role)) {
      productTags.addAll(
          Variant.findByRole(role, is3rdPartyMigration, isMetered).stream()
              .map(Variant::getTag)
              .collect(Collectors.toSet()));
    }

    if (Objects.nonNull(engIds) && !engIds.isEmpty()) {
      engIds.stream()
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
                          .anyMatch(engIds::contains)) {
                    tags.add(additionalTag);
                  }
                }

                productTags.addAll(tags);
              });
    }
    // if not found, let's use the product name
    if ((productTags.isEmpty()) && !isNullOrEmpty(productName)) {
      productTags.addAll(
          Variant.filterVariantsByProductName(productName, is3rdPartyMigration, isMetered)
              .filter(v -> v.getSubscription().isPaygEligible() == isMetered)
              .map(Variant::getTag)
              .collect(Collectors.toSet()));
    }

    SubscriptionDefinition.pruneIncludedProducts(productTags);

    return productTags;
  }

  /**
   * An engineering id can be found in either a fingerprint or variant. Check the variant first. If
   * not found, check the fingerprint.
   *
   * @param engProductId
   * @return Optional<Subscription> subscription
   */
  public static Set<SubscriptionDefinition> lookupSubscriptionByEngId(String engProductId) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> variant.getEngineeringIds().contains(engProductId)))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Looks for role matching a variant
   *
   * @param role
   * @return Optional<Subscription>
   */
  public static Optional<SubscriptionDefinition> lookupSubscriptionByRole(String role) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> variant.getRoles().contains(role)))
        .collect(MoreCollectors.toOptional());
  }

  /**
   * Looks for tag matching a variant
   *
   * @param tag
   * @return Optional<Subscription>
   */
  public static Optional<SubscriptionDefinition> lookupSubscriptionByTag(
      @NotNull @NotEmpty String tag) {

    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> Objects.equals(tag, variant.getTag())))
        .collect(MoreCollectors.toOptional());
  }

  public static boolean isContractEnabled(@NotNull @NotEmpty String tag) {
    return lookupSubscriptionByTag(tag)
        .map(SubscriptionDefinition::isContractEnabled)
        .orElse(false);
  }

  public static boolean isVdcType(@NotNull @NotEmpty String id) {
    return lookupSubscriptionByTag(id).map(SubscriptionDefinition::isVdcType).orElse(false);
  }

  public boolean isPaygEligible() {
    return metrics.stream()
        .anyMatch(metric -> metric.getRhmMetricId() != null || metric.getAwsDimension() != null);
  }

  public static String getAwsDimension(String productId, String metricId) {
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(com.redhat.swatch.configuration.registry.Metric::getAwsDimension)
        .orElse(null);
  }

  public static String getAzureDimension(String productId, String metricId) {
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(com.redhat.swatch.configuration.registry.Metric::getAzureDimension)
        .orElse(null);
  }

  public static String getRhmMetricId(String productId, String metricId) {
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(com.redhat.swatch.configuration.registry.Metric::getRhmMetricId)
        .orElse(null);
  }

  public static List<SubscriptionDefinition> getSubscriptionDefinitions() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions();
  }

  private static boolean isNullOrEmpty(String str) {
    return str == null || str.isEmpty();
  }

  public static void pruneIncludedProducts(Set<String> productTags) {
    Set<String> exclusions =
        productTags.stream()
            .map(SubscriptionDefinition::lookupSubscriptionByTag)
            .filter(Optional::isPresent)
            .flatMap(s -> s.get().getIncludedSubscriptions().stream())
            .flatMap(
                productId ->
                    SubscriptionDefinition.getAllProductTagsByProductId(productId).stream())
            .collect(Collectors.toSet());

    productTags.removeIf(exclusions::contains);
  }
}
