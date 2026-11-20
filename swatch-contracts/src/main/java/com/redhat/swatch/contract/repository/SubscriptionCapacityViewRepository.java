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
package com.redhat.swatch.contract.repository;

import static com.redhat.swatch.contract.resource.ResourceUtils.ANY;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeBillingAccountId;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeBillingProvider;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeServiceLevel;
import static com.redhat.swatch.contract.resource.ResourceUtils.sanitizeUsage;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.model.SlaUsageDefaults;
import com.redhat.swatch.panache.PanacheSpecificationSupport;
import com.redhat.swatch.panache.Specification;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import java.util.stream.Stream;

@ApplicationScoped
public class SubscriptionCapacityViewRepository
    implements PanacheSpecificationSupport<SubscriptionCapacityView, String> {

  public static final String PHYSICAL = "PHYSICAL";
  public static final String HYPERVISOR = "HYPERVISOR";

  public Stream<SubscriptionCapacityView> streamBy(
      Specification<SubscriptionCapacityView> criteria) {
    return find(SubscriptionCapacityView.class, criteria).stream();
  }

  public static Specification<SubscriptionCapacityView> buildSearchSpecification(String orgId) {
    return buildSearchSpecification(orgId, null, null, null, null, null, null, null, false);
  }

  @SuppressWarnings("java:S107")
  public static Specification<SubscriptionCapacityView> buildSearchSpecification(
      String orgId,
      ProductId productId,
      ReportCategory category,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      String metricId) {
    return buildSearchSpecification(
        orgId,
        productId,
        category,
        serviceLevel,
        usage,
        billingProvider,
        billingAccountId,
        metricId,
        false);
  }

  @SuppressWarnings("java:S107")
  public static Specification<SubscriptionCapacityView> buildSearchSpecification(
      String orgId,
      ProductId productId,
      ReportCategory category,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      String metricId,
      boolean resolveMissingSlaUsage) {
    ServiceLevel sanitizedServiceLevel = sanitizeServiceLevel(serviceLevel);
    Usage sanitizedUsage = sanitizeUsage(usage);
    BillingProvider sanitizedBillingProvider = sanitizeBillingProvider(billingProvider);
    String sanitizedBillingAccountId = sanitizeBillingAccountId(billingAccountId);

    /* The where call allows us to build a Specification object to operate on even if the first
     * specification method we call returns null (which it won't in this case, but it's good
     * practice to handle it). */
    Specification<SubscriptionCapacityView> searchCriteria =
        Specification.where(orgIdEquals(orgId));
    if (productId != null) {
      searchCriteria = searchCriteria.and(productIdEquals(productId));
      if (productId.isOnDemand()) {
        searchCriteria = searchCriteria.and(hasBillingProviderId());
      }
    }
    if (Objects.nonNull(sanitizedServiceLevel)
        && !sanitizedServiceLevel.equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(sanitizedServiceLevel, resolveMissingSlaUsage));
    }
    if (Objects.nonNull(sanitizedUsage) && !sanitizedUsage.equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(sanitizedUsage, resolveMissingSlaUsage));
    }
    if (Objects.nonNull(sanitizedBillingProvider)
        && !sanitizedBillingProvider.equals(BillingProvider._ANY)) {
      searchCriteria = searchCriteria.and(billingProviderEquals(sanitizedBillingProvider));
    }
    if (Objects.nonNull(metricId)) {
      Specification<SubscriptionCapacityView> metricOrUnlimitedSpec =
          hasMetricId(metricId).or(hasUnlimitedUsage(true));
      searchCriteria = searchCriteria.and(metricOrUnlimitedSpec);
    }
    if (Objects.nonNull(category)) {
      searchCriteria = searchCriteria.and(hasCategory(category));
    }
    if (Objects.nonNull(sanitizedBillingAccountId)
        && !ANY.equalsIgnoreCase(sanitizedBillingAccountId)
        && productId != null
        && productId.isOnDemand()) {
      searchCriteria = searchCriteria.and(billingAccountIdStartsWith(sanitizedBillingAccountId));
    }

    return searchCriteria;
  }

  public static Specification<SubscriptionCapacityView> productIdEquals(ProductId productId) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.productTag), productId.getValue());
  }

  public static Specification<SubscriptionCapacityView> hasBillingProviderId() {
    return (root, query, builder) ->
        builder.and(
            builder.isNotNull(root.get(SubscriptionCapacityView_.billingProviderId)),
            builder.notEqual(root.get(SubscriptionCapacityView_.billingProviderId), ""));
  }

  public static Specification<SubscriptionCapacityView> billingAccountIdStartsWith(String value) {
    return (root, query, builder) ->
        builder.like(root.get(SubscriptionCapacityView_.billingAccountId), value + "%");
  }

  public static Specification<SubscriptionCapacityView> slaEquals(
      ServiceLevel sla, boolean resolveMissingValues) {
    if (resolveMissingValues && SlaUsageDefaults.isMissing(sla)) {
      return (root, query, builder) -> builder.disjunction();
    }
    return (root, query, builder) -> {
      var serviceLevelPath = root.get(SubscriptionCapacityView_.serviceLevel);
      if (resolveMissingValues && sla == SlaUsageDefaults.SERVICE_LEVEL) {
        return builder.or(builder.equal(serviceLevelPath, sla), builder.isNull(serviceLevelPath));
      }
      if (!resolveMissingValues && sla == ServiceLevel.EMPTY) {
        return builder.or(builder.equal(serviceLevelPath, sla), builder.isNull(serviceLevelPath));
      }
      return builder.equal(serviceLevelPath, sla);
    };
  }

  public static Specification<SubscriptionCapacityView> slaEquals(ServiceLevel sla) {
    return slaEquals(sla, false);
  }

  public static Specification<SubscriptionCapacityView> usageEquals(
      Usage usage, boolean resolveMissingValues) {
    if (resolveMissingValues && SlaUsageDefaults.isMissing(usage)) {
      return (root, query, builder) -> builder.disjunction();
    }
    return (root, query, builder) -> {
      var usagePath = root.get(SubscriptionCapacityView_.usage);
      if (resolveMissingValues && usage == SlaUsageDefaults.USAGE) {
        return builder.or(builder.equal(usagePath, usage), builder.isNull(usagePath));
      }
      if (!resolveMissingValues && usage == Usage.EMPTY) {
        return builder.or(builder.equal(usagePath, usage), builder.isNull(usagePath));
      }
      return builder.equal(usagePath, usage);
    };
  }

  public static Specification<SubscriptionCapacityView> usageEquals(Usage usage) {
    return usageEquals(usage, false);
  }

  public static Specification<SubscriptionCapacityView> billingProviderEquals(
      BillingProvider billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.billingProvider), billingProvider);
  }

  public static Specification<SubscriptionCapacityView> hasCategory(ReportCategory value) {
    String measurementType = getMeasurementTypeFromCategory(value);
    if (measurementType != null) {
      return handleMetricsFilter("measurement_type", measurementType);
    }

    return null;
  }

  public static Specification<SubscriptionCapacityView> hasMetricId(String value) {
    return handleMetricsFilter("metric_id", MetricId.fromString(value).toString());
  }

  public static String getMeasurementTypeFromCategory(ReportCategory value) {
    var category = HypervisorReportCategory.mapCategory(value);
    if (category != null) {
      return switch (category) {
        case NON_HYPERVISOR -> PHYSICAL;
        case HYPERVISOR -> HYPERVISOR;
      };
    }
    return null;
  }

  public static Specification<SubscriptionCapacityView> orgIdEquals(String orgId) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.orgId), orgId);
  }

  private static Specification<SubscriptionCapacityView> handleMetricsFilter(
      String key, String value) {
    return (root, query, builder) ->
        builder.isTrue(
            builder.function(
                "jsonb_path_exists",
                Boolean.class,
                root.get("metrics"),
                builder.literal("$[*] ? (@." + key + " == \"" + value + "\")")));
  }

  public static Specification<SubscriptionCapacityView> hasUnlimitedUsage(
      Boolean hasUnlimitedUsage) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.hasUnlimitedUsage), hasUnlimitedUsage);
  }

  public static Specification<SubscriptionCapacityView> whereServiceLevelAnyOrEqualTo(
      ServiceLevel requested, boolean resolveMissingValues) {
    if (!hasConcreteServiceLevel(requested)) {
      return (root, query, cb) -> cb.conjunction();
    }
    if (resolveMissingValues) {
      // Utilization treats unmapped values and explicit _ANY as wildcards.
      return slaEquals(requested, true)
          .or((root, query, cb) ->
              root.get(SubscriptionCapacityView_.serviceLevel)
                  .in(ServiceLevel._ANY, ServiceLevel.EMPTY));
    }
    return (root, query, cb) -> {
      var path = root.get(SubscriptionCapacityView_.serviceLevel);
      return cb.or(cb.isNull(path), path.in(ServiceLevel._ANY, ServiceLevel.EMPTY, requested));
    };
  }

  public static Specification<SubscriptionCapacityView> whereServiceLevelAnyOrEqualTo(
      ServiceLevel requested) {
    return whereServiceLevelAnyOrEqualTo(requested, false);
  }

  public static Specification<SubscriptionCapacityView> whereUsageAnyOrEqualTo(
      Usage requested, boolean resolveMissingValues) {
    if (!hasConcreteUsage(requested)) {
      return (root, query, cb) -> cb.conjunction();
    }
    if (resolveMissingValues) {
      return usageEquals(requested, true)
          .or((root, query, cb) ->
              root.get(SubscriptionCapacityView_.usage).in(Usage._ANY, Usage.EMPTY));
    }
    return (root, query, cb) -> {
      var path = root.get(SubscriptionCapacityView_.usage);
      return cb.or(cb.isNull(path), path.in(Usage._ANY, Usage.EMPTY, requested));
    };
  }

  public static Specification<SubscriptionCapacityView> whereUsageAnyOrEqualTo(Usage requested) {
    return whereUsageAnyOrEqualTo(requested, false);
  }

  public static Specification<SubscriptionCapacityView> whereBillingProviderNullSafeEqual(
      BillingProvider expected) {
    if (expected == null) {
      return (root, q, cb) -> cb.isNull(root.get(SubscriptionCapacityView_.billingProvider));
    }
    return (root, q, cb) -> cb.equal(root.get(SubscriptionCapacityView_.billingProvider), expected);
  }

  public static Specification<SubscriptionCapacityView> whereBillingAccountIdNullSafeEqual(
      String expected) {
    if (expected == null) {
      return (root, q, cb) -> cb.isNull(root.get(SubscriptionCapacityView_.billingAccountId));
    }
    return (root, q, cb) ->
        cb.equal(root.get(SubscriptionCapacityView_.billingAccountId), expected);
  }

  private static boolean hasConcreteServiceLevel(ServiceLevel s) {
    return s != null && s != ServiceLevel._ANY && s != ServiceLevel.EMPTY;
  }

  private static boolean hasConcreteUsage(Usage u) {
    return u != null && u != Usage._ANY && u != Usage.EMPTY;
  }
}
