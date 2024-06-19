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
package org.candlepin.subscriptions.db;

import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;
import static org.candlepin.subscriptions.resource.ResourceUtils.sanitizeBillingAccountId;
import static org.candlepin.subscriptions.resource.ResourceUtils.sanitizeBillingProvider;
import static org.candlepin.subscriptions.resource.ResourceUtils.sanitizeServiceLevel;
import static org.candlepin.subscriptions.resource.ResourceUtils.sanitizeUsage;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import java.util.Objects;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v1.model.UsageType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.FluentQuery;

public interface SubscriptionCapacityViewRepository
    extends JpaRepository<SubscriptionCapacityView, String>,
        JpaSpecificationExecutor<SubscriptionCapacityView> {

  String PHYSICAL = "PHYSICAL";
  String HYPERVISOR = "HYPERVISOR";

  default Stream<SubscriptionCapacityView> streamBy(
      Specification<SubscriptionCapacityView> criteria) {
    return findBy(criteria, FluentQuery.FetchableFluentQuery::stream);
  }

  static Specification<SubscriptionCapacityView> buildSearchSpecification(String orgId) {
    return buildSearchSpecification(orgId, null, null, null, null, null, null, null);
  }

  static Specification<SubscriptionCapacityView> buildSearchSpecification( // NOSONAR
      String orgId,
      ProductId productId,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String metricId) {
    ServiceLevel sanitizedServiceLevel = sanitizeServiceLevel(serviceLevel);
    Usage sanitizedUsage = sanitizeUsage(usage);
    BillingProvider sanitizedBillingProvider = sanitizeBillingProvider(billingProviderType);
    String sanitizedBillingAccountId = sanitizeBillingAccountId(billingAccountId);

    /* The where call allows us to build a Specification object to operate on even if the first
     * specification method we call returns null (which it won't in this case, but it's good
     * practice to handle it). */
    Specification<SubscriptionCapacityView> searchCriteria =
        Specification.where(orgIdEquals(orgId));
    if (productId != null) {
      searchCriteria = searchCriteria.and(productIdEquals(productId));
      if (productId.isPayg() || productId.isPrometheusEnabled()) {
        searchCriteria = searchCriteria.and(hasBillingProviderId());
      }
    }
    if (Objects.nonNull(sanitizedServiceLevel)
        && !sanitizedServiceLevel.equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(sanitizedServiceLevel));
    }
    if (Objects.nonNull(sanitizedUsage) && !sanitizedUsage.equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(sanitizedUsage));
    }
    if (Objects.nonNull(sanitizedBillingProvider)
        && !sanitizedBillingProvider.equals(BillingProvider._ANY)) {
      searchCriteria = searchCriteria.and(billingProviderEquals(sanitizedBillingProvider));
    }
    if (Objects.nonNull(metricId)) {
      searchCriteria = searchCriteria.and(hasMetricId(metricId));
    }
    if (Objects.nonNull(category)) {
      searchCriteria = searchCriteria.and(hasCategory(category));
    }
    if (Objects.nonNull(sanitizedBillingAccountId)
        && !ANY.equalsIgnoreCase(sanitizedBillingAccountId)
        && productId != null
        && productId.isPrometheusEnabled()) {
      searchCriteria = searchCriteria.and(billingAccountIdStartsWith(sanitizedBillingAccountId));
    }

    return searchCriteria;
  }

  static Specification<SubscriptionCapacityView> productIdEquals(ProductId productId) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.productTag), productId.getValue());
  }

  static Specification<SubscriptionCapacityView> hasBillingProviderId() {
    return (root, query, builder) ->
        builder.and(
            builder.isNotNull(root.get(SubscriptionCapacityView_.billingProviderId)),
            builder.notEqual(root.get(SubscriptionCapacityView_.billingProviderId), ""));
  }

  static Specification<SubscriptionCapacityView> billingAccountIdStartsWith(String value) {
    return (root, query, builder) ->
        builder.like(root.get(SubscriptionCapacityView_.billingAccountId), value + "%");
  }

  static Specification<SubscriptionCapacityView> slaEquals(ServiceLevel sla) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.serviceLevel), sla);
  }

  static Specification<SubscriptionCapacityView> usageEquals(Usage usage) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.usage), usage);
  }

  static Specification<SubscriptionCapacityView> billingProviderEquals(
      BillingProvider billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionCapacityView_.billingProvider), billingProvider);
  }

  static Specification<SubscriptionCapacityView> hasCategory(ReportCategory value) {
    String measurementType = getMeasurementTypeFromCategory(value);
    if (measurementType != null) {
      return handleMetricsFilter("measurement_type", measurementType);
    }

    return null;
  }

  static Specification<SubscriptionCapacityView> hasMetricId(String value) {
    return handleMetricsFilter("metric_id", MetricId.fromString(value).toString());
  }

  static String getMeasurementTypeFromCategory(ReportCategory value) {
    var category = HypervisorReportCategory.mapCategory(value);
    if (category != null) {
      return switch (category) {
        case NON_HYPERVISOR -> PHYSICAL;
        case HYPERVISOR -> HYPERVISOR;
      };
    }
    return null;
  }

  static Specification<SubscriptionCapacityView> orgIdEquals(String orgId) {
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
}
