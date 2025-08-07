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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
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
import java.util.concurrent.TimeUnit;
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

  protected static LoadingCache<String, Optional<SubscriptionDefinition>> tagCache =
      Caffeine.newBuilder()
          .expireAfterAccess(3, TimeUnit.HOURS)
          .build(SubscriptionDefinition::cacheSubscriptionByTag);

  protected static LoadingCache<String, Optional<SubscriptionDefinition>> roleCache =
      Caffeine.newBuilder()
          .expireAfterAccess(3, TimeUnit.HOURS)
          .build(SubscriptionDefinition::cacheSubscriptionByRole);

  protected static LoadingCache<String, Set<SubscriptionDefinition>> engIdCache =
      Caffeine.newBuilder()
          .expireAfterAccess(3, TimeUnit.HOURS)
          .build(SubscriptionDefinition::cacheSubscriptionByEngId);

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
   * Defines an "in-the-box" subscription. Considered included from both usage and capacity
   * perspectives.
   */
  @Default private Set<String> includedSubscriptions = new HashSet<>();

  @Valid @Default private Set<Variant> variants = new HashSet<>();
  private String serviceType;
  @Valid @Default private Set<Metric> metrics = new HashSet<>();
  private Defaults defaults;
  private boolean contractEnabled;
  private boolean payg;
  private boolean vdcType;

  /**
   * Search for a given service type.
   *
   * @param serviceType the service type to search for
   * @return an Optional&lt;Subscription&gt;
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
   * Return a list of all service types among the subscription definitions.
   *
   * @return Set&lt;String&gt; serviceTypes
   */
  public static Set<String> getAllServiceTypes() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getServiceType)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public boolean isPrometheusEnabled() {
    return this.getMetrics().stream().anyMatch(metric -> Objects.nonNull(metric.getPrometheus()));
  }

  public static boolean isFinestGranularity(String tag, String granularity) {
    String finestTagGranularity = getFinestGranularity(tag);

    // Compare as strings because granularity is represented by two different enumerations:
    // org.candlepin.subscriptions.db.model.Granularity and
    // com.redhat.swatch.configuration.registry.SubscriptionDefinitionGranularity
    return finestTagGranularity.equalsIgnoreCase(granularity);
  }

  public static String getFinestGranularity(String tag) {
    var subscription =
        SubscriptionDefinition.lookupSubscriptionByTag(tag)
            .orElseThrow(
                () -> new IllegalStateException(tag + " missing in subscription configuration"));

    return subscription.getFinestGranularity().toString();
  }

  public SubscriptionDefinitionGranularity getFinestGranularity() {
    return this.isPaygEligible()
        ? SubscriptionDefinitionGranularity.HOURLY
        : SubscriptionDefinitionGranularity.DAILY;
  }

  public List<SubscriptionDefinitionGranularity> getSupportedGranularity() {
    List<SubscriptionDefinitionGranularity> granularity =
        new ArrayList<>(List.of(SubscriptionDefinitionGranularity.values()));

    if (!isPaygEligible()) {
      granularity.remove(SubscriptionDefinitionGranularity.HOURLY);
    }

    return granularity;
  }

  public static double getBillingFactor(String tag, String metricId) {
    var metricOptional =
        Variant.findByTag(tag)
            .map(Variant::getSubscription)
            .flatMap(
                subscriptionDefinition ->
                    subscriptionDefinition.getMetric(MetricId.fromString(metricId).getValue()));
    return metricOptional.map(Metric::getBillingFactor).orElse(1.0);
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

    // First identify variants by PAYG status and one of the required product identifiers
    Set<Variant> filteredVariants = getVariantsMatchingPAYGStatusAndRequiredIdentifier(params);

    // Then filter down further with more criteria
    Set<Variant> productTags =
        filteredVariants.stream()
            .filter(createOptionalConversionPredicate(params))
            .filter(createOptionalMetricIdsPredicate(params))
            .collect(Collectors.toSet());

    return productTags.stream().map(Variant::getTag).collect(Collectors.toSet());
  }

  /**
   * In order to get a product tag, you must supply either level, role, or engId data.
   *
   * @param params the search parameters to use
   * @return Set&amp;lt;Variant&amp;gt;
   */
  private static Set<Variant> getVariantsMatchingPAYGStatusAndRequiredIdentifier(
      ProductTagLookupParams params) {

    var variants =
        SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
            // NOTE(khowell): we filter by payg/non-payg early in order to avoid role values from
            // non-payg (RHEL for x86) from being identified for payg (e.g. rhel-for-x86-els-addon)
            .filter(
                s ->
                    params.getIsPaygEligibleProduct() == null
                        || Objects.equals(s.isPaygEligible(), params.getIsPaygEligibleProduct()))
            .flatMap(subscription -> subscription.getVariants().stream())
            .collect(Collectors.toSet());

    Set<Variant> filteredVariants =
        variants.stream()
            // order of predicates is important. When one applies, we stop checking the rest.
            .filter(
                createLevelPredicate(params)
                    .or(createRolePredicate(params))
                    .or(createEngIdPredicate(params)))
            .collect(Collectors.toSet());

    if (filteredVariants.isEmpty()) {
      log.info("No variants found to uniquely identify a product based on {}", params);
      return Set.of();
    } else {
      return filteredVariants;
    }
  }

  // EngIds can occur in more than one SubscriptionDefinition
  protected static Set<SubscriptionDefinition> cacheSubscriptionByEngId(String engProductId) {
    int engId = Integer.parseInt(engProductId);
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(
            subscription ->
                !subscription.getVariants().isEmpty()
                    && subscription.getVariants().stream()
                        .anyMatch(variant -> variant.getEngineeringIds().contains(engId)))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * An engineering id can be found in either a fingerprint or variant. Check the variant first. If
   * not found, check the fingerprint.
   *
   * @param engProductId a String with the engineering product ID
   * @return Optional&lt;Subscription&gt; subscription
   */
  public static Set<SubscriptionDefinition> lookupSubscriptionByEngId(String engProductId) {
    return engIdCache.get(engProductId);
  }

  protected static Optional<SubscriptionDefinition> cacheSubscriptionByRole(String role) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(
            subscription ->
                !subscription.getVariants().isEmpty()
                    && subscription.getVariants().stream()
                        .anyMatch(variant -> variant.getRoles().contains(role)))
        .collect(MoreCollectors.toOptional());
  }

  /**
   * Looks for SubscriptionDefinitiion matching a role
   *
   * @param role a String with the role value
   * @return Optional&lt;Subscription&gt;
   */
  public static Optional<SubscriptionDefinition> lookupSubscriptionByRole(String role) {
    return roleCache.get(role);
  }

  protected static Optional<SubscriptionDefinition> cacheSubscriptionByTag(
      @NotNull @NotEmpty String tag) {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .filter(
            subscription ->
                !subscription.getVariants().isEmpty()
                    && subscription.getVariants().stream()
                        .anyMatch(variant -> Objects.equals(tag, variant.getTag())))
        .collect(MoreCollectors.toOptional());
  }

  /**
   * Looks for tag matching a tag
   *
   * @param tag a String with the tag value
   * @return Optional&lt;Subscription&gt;
   */
  public static Optional<SubscriptionDefinition> lookupSubscriptionByTag(
      @NotNull @NotEmpty String tag) {
    return tagCache.get(tag);
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
    return payg;
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

  public static boolean isMetricGratis(String productId, MetricId metricId) {
    String metricIdAsString = metricId.toString();
    return lookupSubscriptionByTag(productId)
        .flatMap(subscriptionDefinition -> subscriptionDefinition.getMetric(metricIdAsString))
        .map(m -> Boolean.TRUE.equals(m.getEnableGratisUsage()))
        .orElse(false);
  }

  public static List<SubscriptionDefinition> getSubscriptionDefinitions() {
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions();
  }

  public static void pruneIncludedProducts(Collection<String> productTags) {
    if (productTags == null) {
      return;
    }

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
   * @return a Predicate as described above
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
   * @return a Predicate as described above
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
   * @return a Predicate as described above
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
   * Returns a predicate only considering the third-party migration conversion status if it's
   * specified.
   *
   * @param params ProductTagLookupParams
   * @return a Predicate as described above
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
   * @return a Predicate as described above
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
