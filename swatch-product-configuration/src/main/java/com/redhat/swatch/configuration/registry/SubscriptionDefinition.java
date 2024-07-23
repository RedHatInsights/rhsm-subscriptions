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
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
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
  public static final Set<String> ORDERED_GRANULARITY =
      Set.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");

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
  @Default private Set<String> includedSubscriptions = new HashSet<>();

  @Valid @Default private Set<Variant> variants = new HashSet<>();
  private String serviceType;
  @Valid @Default private Set<Metric> metrics = new HashSet<>();
  private Defaults defaults;
  private boolean contractEnabled;
  private boolean vdcType;

  /**
   * @param serviceType
   * @return Optional<Subscription>
   */
  public static Set<SubscriptionDefinition> findByServiceType(String serviceType) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getServiceType(), serviceType))
        .collect(Collectors.toSet());
  }

  public Set<String> getMetricIds() {
    return this.getMetrics().stream()
        .map(Metric::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
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
   * @return Set<String> serviceTypes
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

  @SafeVarargs
  public static Set<Variant> filterVariants(Predicate<Variant>... predicates) {
    Set<Variant> variants =
        SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
            .flatMap(subscription -> subscription.getVariants().stream())
            .collect(Collectors.toSet());

    // Combine all predicates into a single predicate using reduce
    Predicate<Variant> combinedPredicate =
        Stream.of(predicates).reduce(predicate -> true, Predicate::and);

    return variants.stream().filter(combinedPredicate).collect(Collectors.toSet());
  }

  public static Set<String> getAllProductTagsByProductId(String id) {
    return SubscriptionDefinition.findById(id)
        .map(s -> s.getVariants().stream().map(Variant::getTag).collect(Collectors.toSet()))
        .orElse(Set.of());
  }

  public static Set<String> getAllProductTags(ProductTagLookupParams params) {

    // First identify variants by one of the required product identifiers
    Set<Variant> filteredVariants = getVariantsMatchingRequiredIdentifier(params);

    // Then filter down further with more criteria
    Set<Variant> productTags =
        filteredVariants.stream()
            .filter(createOptionalMeteredPredicate(params))
            .filter(createOptionalConversionPredicate(params))
            .filter(createOptionalMetricIdsPredicate(params))
            .collect(Collectors.toSet());

    return productTags.stream().map(Variant::getTag).collect(Collectors.toSet());
  }

  /**
   * In order to get a product tag, you must supply either level, role, engId, or productName data.
   *
   * @param params
   * @return Set<Variant>
   */
  private static Set<Variant> getVariantsMatchingRequiredIdentifier(ProductTagLookupParams params) {

    var variants =
        SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
            .flatMap(subscription -> subscription.getVariants().stream())
            .collect(Collectors.toSet());

    List<Predicate<Variant>> sequentialPredicates =
        List.of(
            createLevelPredicate(params),
            createRolePredicate(params),
            createEngIdPredicate(params),
            createProductNamePredicate(params));

    Set<Variant> filteredVariants = new HashSet<>();

    for (Predicate<Variant> predicate : sequentialPredicates) {
      filteredVariants = variants.stream().filter(predicate).collect(Collectors.toSet());
      if (!filteredVariants.isEmpty()) {
        break;
      }
    }

    if (filteredVariants.isEmpty()) {
      log.info("No variants found to uniquely identify a product based on {}", params);
      return Set.of();
    } else {
      return filteredVariants;
    }
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

  public static String getAwsDimension(String productTag, String metricId) {
    return lookupSubscriptionByTag(productTag)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(Metric::getAwsDimension)
        .orElse(null);
  }

  public static String getAzureDimension(String productId, String metricId) {
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(Metric::getAzureDimension)
        .orElse(null);
  }

  public static String getRhmMetricId(String productId, String metricId) {
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricId))
        .map(Metric::getRhmMetricId)
        .orElse(null);
  }

  public static List<SubscriptionDefinition> getSubscriptionDefinitions() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions();
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

  /**
   * Returns a predicate that only evaluates to true if Level1 AND Level2 parameters are specified
   * AND both match the same Variant.
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createLevelPredicate(ProductTagLookupParams params) {
    Predicate<Variant> level1Predicate =
        variant -> {
          String paramLevel1 = params.getLevel1();
          String variantLevel1 = variant.getLevel1();
          return !isNullOrEmpty(paramLevel1)
              && !isNullOrEmpty(variantLevel1)
              && Objects.equals(paramLevel1, variantLevel1);
        };

    Predicate<Variant> level2Predicate =
        variant -> {
          String paramLevel2 = params.getLevel2();
          String variantLevel2 = variant.getLevel2();
          return !isNullOrEmpty(paramLevel2)
              && !isNullOrEmpty(variantLevel2)
              && Objects.equals(paramLevel2, variantLevel2);
        };

    return level1Predicate.and(level2Predicate);
  }

  /**
   * Returns a predicate that only evaluates to true if engIds parameter is specified and matches a
   * variant
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createEngIdPredicate(ProductTagLookupParams params) {
    return variant -> {
      var paramEngIds = params.getEngIds();
      Set<Integer> variantEngIds = variant.getEngineeringIds();
      return !isNullOrEmpty(paramEngIds)
          && !isNullOrEmpty(variantEngIds)
          && paramEngIds.stream().anyMatch(variantEngIds::contains);
    };
  }

  /**
   * Returns a predicate that only evaluates to true if role parameter is specified and matches a
   * variant
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createRolePredicate(ProductTagLookupParams params) {
    return variant -> {
      String paramRole = params.getRole();
      Set<String> variantRoles = variant.getRoles();
      return !isNullOrEmpty(paramRole)
          && !isNullOrEmpty(variantRoles)
          && variantRoles.contains(paramRole);
    };
  }

  /**
   * Returns a predicate that only evaluates to true if product name parameter is specified and
   * matches a variant
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createProductNamePredicate(ProductTagLookupParams params) {
    return variant -> {
      String paramProductName = params.getProductName();
      Set<String> variantProductNames = variant.getProductNames();
      return !isNullOrEmpty(paramProductName)
          && !isNullOrEmpty(variantProductNames)
          && variantProductNames.contains(paramProductName);
    };
  }

  /**
   * Returns a predicate only considering the isPaygEligible status if it's specified.
   *
   * @param params ProductTagLookupParams
   * @return a predicate that always evaluates to 'true' if the isPaygEligible param is not
   *     specified, resulting in nothing being filtered out. Otherwise a predicate that will include
   *     Variants where the isPaygEligible values match
   */
  private static Predicate<Variant> createOptionalMeteredPredicate(ProductTagLookupParams params) {
    return variant -> {
      Boolean paramsIsPaygEligible = params.getIsPaygEligibleProduct();
      Boolean variantIsPaygEligible =
          variant.getSubscription() != null ? variant.getSubscription().isPaygEligible() : null;
      return paramsIsPaygEligible == null
          || Objects.equals(variantIsPaygEligible, paramsIsPaygEligible);
    };
  }

  /**
   * Returns a predicate only considering the third-party migration conversion status if it's
   * specified.
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createOptionalConversionPredicate(
      ProductTagLookupParams params) {
    return variant -> {
      Boolean isVariantConverted = variant.getIsMigrationProduct();
      Boolean isParamConverted = params.getIs3rdPartyMigration();
      return isParamConverted == null || Objects.equals(isVariantConverted, isParamConverted);
    };
  }

  /**
   * Returns a predicate only considering configured metric ids if it's specified. Helpful for only
   * getting relevant product tags (i.e. if you're doing a nightly tally and have a system with
   * engId 204 and measurements for Sockets, you won't get a els-payg tag, since vCPUs are only
   * relevant for those tags.)
   *
   * @param params ProductTagLookupParams
   * @return Predicate<Variant>
   */
  private static Predicate<Variant> createOptionalMetricIdsPredicate(
      ProductTagLookupParams params) {
    return variant -> {
      Set<String> paramMetricIds = params.getMetricIds();
      Set<String> variantMetricIds =
          variant.getSubscription() != null ? variant.getSubscription().getMetricIds() : Set.of();
      return isNullOrEmpty(paramMetricIds)
          || paramMetricIds.stream().anyMatch(variantMetricIds::contains);
    };
  }

  private static boolean isNullOrEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  private static boolean isNullOrEmpty(String str) {
    return str == null || str.isEmpty();
  }
}
