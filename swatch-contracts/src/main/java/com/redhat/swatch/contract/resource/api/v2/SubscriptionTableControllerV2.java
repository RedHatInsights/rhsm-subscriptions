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
package com.redhat.swatch.contract.resource.api.v2;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.openapi.model.BillingProviderType;
import com.redhat.swatch.contract.openapi.model.ReportCategory;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportSortV2;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV1Meta;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.openapi.model.SkuCapacitySubscription;
import com.redhat.swatch.contract.openapi.model.SkuCapacityV2;
import com.redhat.swatch.contract.openapi.model.SortDirection;
import com.redhat.swatch.contract.openapi.model.SubscriptionEventType;
import com.redhat.swatch.contract.openapi.model.SubscriptionType;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.contract.resource.InMemoryPager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

@ApplicationScoped
@Slf4j
public class SubscriptionTableControllerV2 {

  private final ApiModelMapperV2 mapper;
  private final SubscriptionCapacityViewRepository repository;
  private final ApplicationClock clock;

  @Context SecurityContext securityContext;

  @Inject
  SubscriptionTableControllerV2(
      ApiModelMapperV2 mapper,
      SubscriptionCapacityViewRepository repository,
      ApplicationClock clock) {
    this.mapper = mapper;
    this.repository = repository;
    this.clock = clock;
  }

  @SuppressWarnings("java:S107")
  @Transactional
  public SkuCapacityReportV2 capacityReportBySkuV2(
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String metricId,
      SkuCapacityReportSortV2 sort,
      SortDirection dir) {

    var subscriptionSpec =
        SubscriptionCapacityViewRepository.buildSearchSpecification(
            getOrgId(),
            productId,
            mapper.map(category),
            mapper.map(serviceLevel),
            mapper.map(usage),
            mapper.map(billingProviderType),
            billingAccountId,
            metricId);
    MetricsMeta metrics = new MetricsMeta(metricId, productId);
    Map<InventoryKey, SkuCapacityV2> reportItemsBySku = new HashMap<>();
    repository
        .streamBy(subscriptionSpec)
        .forEach(
            subscription -> {
              InventoryKey inventoryKey = new InventoryKey(productId, subscription);

              SkuCapacityV2 inventory =
                  reportItemsBySku.computeIfAbsent(
                      inventoryKey, s -> initializeSkuCapacity(subscription, metrics, category));
              calculateNextEvent(subscription, inventory);
              if (productId.isOnDemand()) {
                addOnDemandSubscriptionInformation(subscription, inventory);
              } else {
                addSubscriptionInformation(subscription, inventory);
              }

              subscription.getMetrics().stream()
                  .filter(m -> m.getCapacity() != null)
                  .filter(m -> m.getMetricId() != null)
                  .filter(metrics::isSupported)
                  .filter(filterByCategory(inventory))
                  .forEach(
                      entry -> {
                        int position =
                            metrics.getPosition(MetricId.fromString(entry.getMetricId()));
                        inventory
                            .getMeasurements()
                            .set(
                                position,
                                inventory.getMeasurements().get(position) + entry.getCapacity());
                      });
            });

    List<SkuCapacityV2> reportItems = new ArrayList<>(reportItemsBySku.values());
    int reportItemCount = reportItems.size();
    // The pagination and sorting of capacities is done in memory and can cause performance
    // issues
    // As an improvement this should be pushed lower into the Repository layer
    sortCapacities(reportItems, sort, dir);
    var page = InMemoryPager.paginate(reportItems, offset, limit);

    return new SkuCapacityReportV2()
        .data(page)
        .meta(
            new SkuCapacityReportV1Meta()
                .subscriptionType(
                    productId.isOnDemand() ? SubscriptionType.ON_DEMAND : SubscriptionType.ANNUAL)
                .count(reportItemCount)
                .serviceLevel(serviceLevel)
                .usage(usage)
                .measurements(metrics.stream().map(MetricId::toString).toList())
                .reportCategory(category)
                .product(productId.toString()));
  }

  private void addOnDemandSubscriptionInformation(
      SubscriptionCapacityView subscription, SkuCapacityV2 skuCapacity) {
    var invSub = new SkuCapacitySubscription();
    invSub.setId(subscription.getSubscriptionId());
    Optional.ofNullable(subscription.getSubscriptionNumber()).ifPresent(invSub::setNumber);
    skuCapacity.addSubscriptionsItem(invSub);
    skuCapacity.setQuantity(skuCapacity.getQuantity() + (int) subscription.getQuantity());
    OffsetDateTime subEnd = subscription.getEndDate();
    OffsetDateTime nearestEventDate = skuCapacity.getNextEventDate();
    if (subEnd != null && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
      nearestEventDate = subEnd;
      skuCapacity.setNextEventDate(nearestEventDate);
      skuCapacity.setNextEventType(SubscriptionEventType.END);
    }
  }

  private void addSubscriptionInformation(
      SubscriptionCapacityView subscription, SkuCapacityV2 skuCapacity) {
    var invSub = new SkuCapacitySubscription();
    invSub.setId(subscription.getSubscriptionId());
    Optional.ofNullable(subscription.getSubscriptionNumber()).ifPresent(invSub::setNumber);
    // Different measurements can have the same subscription.  I'm not crazy about this
    // implementation but refining it is for another day.

    if (!skuCapacity.getSubscriptions().contains(invSub)) {
      skuCapacity.addSubscriptionsItem(invSub);
      skuCapacity.setQuantity(skuCapacity.getQuantity() + (int) subscription.getQuantity());
    }
  }

  private void calculateNextEvent(
      SubscriptionCapacityView subscription, SkuCapacityV2 skuCapacity) {

    OffsetDateTime nearestEventDate = skuCapacity.getNextEventDate();
    OffsetDateTime subEnd = subscription.getEndDate();
    if (subEnd != null
        && clock.now().isBefore(subEnd)
        && (nearestEventDate == null || subEnd.isBefore(nearestEventDate))) {
      nearestEventDate = subEnd;
      skuCapacity.setNextEventDate(nearestEventDate);
      skuCapacity.setNextEventType(SubscriptionEventType.END);
    }
  }

  private static void sortCapacities(
      List<SkuCapacityV2> items, SkuCapacityReportSortV2 sort, SortDirection dir) {
    items.sort(
        (left, right) -> {
          var sortField = Optional.ofNullable(sort).orElse(SkuCapacityReportSortV2.SKU);
          int sortDir = 1;
          if (dir == SortDirection.DESC) {
            sortDir = -1;
          }
          int diff =
              switch (sortField) {
                case SKU -> left.getSku().compareTo(right.getSku());
                case SERVICE_LEVEL -> left.getServiceLevel().compareTo(right.getServiceLevel());
                case USAGE -> left.getUsage().compareTo(right.getUsage());
                case QUANTITY -> left.getQuantity().compareTo(right.getQuantity());
                case NEXT_EVENT_DATE -> left.getNextEventDate().compareTo(right.getNextEventDate());
                case NEXT_EVENT_TYPE -> left.getNextEventType().compareTo(right.getNextEventType());
                case PRODUCT_NAME -> left.getProductName().compareTo(right.getProductName());
              };
          // If the two items are sorted by some other field than SKU and are equal, then break the
          // tie by sorting by SKU. No two SKUs in the list are equal.
          if (diff == 0 && sortField != SkuCapacityReportSortV2.SKU) {
            diff = left.getSku().compareTo(right.getSku());
          }

          return diff * sortDir;
        });
  }

  private Predicate<SubscriptionCapacityViewMetric> filterByCategory(SkuCapacityV2 inventory) {
    return m -> {
      if (inventory.getCategory() != null) {
        HardwareMeasurementType type = HardwareMeasurementType.fromString(m.getMeasurementType());
        return type != null
            && inventory.getCategory().equals(mapper.measurementTypeToReportCategory(type));
      }

      return true;
    };
  }

  private SkuCapacityV2 initializeSkuCapacity(
      @Nonnull SubscriptionCapacityView sub,
      @Nonnull MetricsMeta metrics,
      ReportCategory category) {
    var inventory = new SkuCapacityV2();
    inventory.setSubscriptions(new ArrayList<>());
    inventory.setSku(sub.getSku());
    inventory.setProductName(sub.getProductName());
    inventory.setServiceLevel(
        mapper.map(Optional.ofNullable(sub.getServiceLevel()).orElse(ServiceLevel.EMPTY)));
    inventory.setUsage(mapper.map(Optional.ofNullable(sub.getUsage()).orElse(Usage.EMPTY)));
    inventory.setHasInfiniteQuantity(sub.getHasUnlimitedUsage());
    inventory.setBillingProvider(
        mapper.map(Optional.ofNullable(sub.getBillingProvider()).orElse(BillingProvider.EMPTY)));
    inventory.setQuantity(0);
    inventory.setMeasurements(new ArrayList<>(Collections.nCopies(metrics.size(), 0.0)));
    if (category != null) {
      inventory.setCategory(category);
    } else {
      // inspect the subscription measurements
      inventory.setCategory(
          sub.getMetrics().stream()
              .map(m -> HardwareMeasurementType.fromString(m.getMeasurementType()))
              .filter(Objects::nonNull)
              .findFirst()
              .map(mapper::measurementTypeToReportCategory)
              .orElse(null));
    }

    return inventory;
  }

  @EqualsAndHashCode
  private static class InventoryKey {
    private final String sku;
    private BillingProvider billingProvider;

    public InventoryKey(ProductId productId, SubscriptionCapacityView subscription) {
      sku = subscription.getSku();
      if (productId.isOnDemand()) {
        billingProvider = subscription.getBillingProvider();
      }
    }
  }

  private static class MetricsMeta {

    private final List<MetricId> metrics;
    private final Map<MetricId, Integer> positionOfMetrics;

    MetricsMeta(String metricIdFromRequest, ProductId productId) {
      metrics =
          Optional.ofNullable(metricIdFromRequest)
              .map(MetricId::fromString)
              .map(List::of)
              .orElseGet(() -> getMetricsFromProduct(productId));
      positionOfMetrics = new HashMap<>();
      for (int index = 0; index < metrics.size(); index++) {
        positionOfMetrics.put(metrics.get(index), index);
      }
    }

    private static List<MetricId> getMetricsFromProduct(ProductId productId) {
      return MetricIdUtils.getMetricIdsFromConfigForTag(productId.toString())
          .sorted(Comparator.comparing(MetricId::getValue))
          .toList();
    }

    public Stream<MetricId> stream() {
      return metrics.stream();
    }

    public int size() {
      return metrics.size();
    }

    public boolean isSupported(SubscriptionCapacityViewMetric metric) {
      return positionOfMetrics.containsKey(MetricId.fromString(metric.getMetricId()));
    }

    public int getPosition(MetricId metricId) {
      return positionOfMetrics.get(metricId);
    }
  }

  private String getOrgId() {
    return securityContext.getUserPrincipal().getName();
  }

  // for unit testing since we have no REST request
  protected void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }
}
