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
package org.candlepin.subscriptions.resource.api.v2;

import static org.candlepin.subscriptions.resource.ResourceUtils.getOrgId;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityViewMetric;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApiModelMapperV2;
import org.candlepin.subscriptions.util.InMemoryPager;
import org.candlepin.subscriptions.utilization.api.v2.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v2.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v2.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacity;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReport;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReportMeta;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReportSort;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacitySubscription;
import org.candlepin.subscriptions.utilization.api.v2.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.v2.model.SubscriptionEventType;
import org.candlepin.subscriptions.utilization.api.v2.model.SubscriptionType;
import org.candlepin.subscriptions.utilization.api.v2.model.UsageType;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service(value = "v2SubscriptionTableController")
@Slf4j
@AllArgsConstructor
public class SubscriptionTableController {

  private final ApiModelMapperV2 mapper;
  private final SubscriptionCapacityViewRepository repository;
  private final ApplicationClock clock;

  @Transactional
  public SkuCapacityReport capacityReportBySku( // NOSONAR
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType serviceLevel,
      UsageType usage,
      BillingProviderType billingProviderType,
      String billingAccountId,
      String metricId,
      SkuCapacityReportSort sort,
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
    Map<InventoryKey, SkuCapacity> reportItemsBySku = new HashMap<>();
    repository
        .streamBy(subscriptionSpec)
        .forEach(
            subscription -> {
              InventoryKey inventoryKey = new InventoryKey(productId, subscription);

              SkuCapacity inventory =
                  reportItemsBySku.computeIfAbsent(
                      inventoryKey, s -> initializeSkuCapacity(subscription, metrics, category));
              calculateNextEvent(subscription, inventory);
              if (productId.isPrometheusEnabled()) {
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

    List<SkuCapacity> reportItems = new ArrayList<>(reportItemsBySku.values());
    int reportItemCount = reportItems.size();
    // The pagination and sorting of capacities is done in memory and can cause performance
    // issues
    // As an improvement this should be pushed lower into the Repository layer
    sortCapacities(reportItems, sort, dir);
    Pageable pageable = ResourceUtils.getPageable(offset, limit);
    var page = InMemoryPager.paginate(reportItems, pageable);

    return new SkuCapacityReport()
        .data(page.getContent())
        .meta(
            new SkuCapacityReportMeta()
                .subscriptionType(
                    productId.isPrometheusEnabled()
                        ? SubscriptionType.ON_DEMAND
                        : SubscriptionType.ANNUAL)
                .count(reportItemCount)
                .serviceLevel(serviceLevel)
                .usage(usage)
                .measurements(metrics.stream().map(MetricId::toString).toList())
                .reportCategory(category)
                .product(productId.toString()));
  }

  private void addOnDemandSubscriptionInformation(
      SubscriptionCapacityView subscription, SkuCapacity skuCapacity) {
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
      SubscriptionCapacityView subscription, SkuCapacity skuCapacity) {
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

  private void calculateNextEvent(SubscriptionCapacityView subscription, SkuCapacity skuCapacity) {

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
      List<SkuCapacity> items, SkuCapacityReportSort sort, SortDirection dir) {
    items.sort(
        (left, right) -> {
          var sortField = Optional.ofNullable(sort).orElse(SkuCapacityReportSort.SKU);
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
          if (diff == 0 && sortField != SkuCapacityReportSort.SKU) {
            diff = left.getSku().compareTo(right.getSku());
          }

          return diff * sortDir;
        });
  }

  private Predicate<SubscriptionCapacityViewMetric> filterByCategory(SkuCapacity inventory) {
    return m -> {
      if (inventory.getCategory() != null) {
        HardwareMeasurementType type = HardwareMeasurementType.fromString(m.getMeasurementType());
        return type != null
            && inventory.getCategory().equals(mapper.measurementTypeToReportCategory(type));
      }

      return true;
    };
  }

  private SkuCapacity initializeSkuCapacity(
      @Nonnull SubscriptionCapacityView sub,
      @Nonnull MetricsMeta metrics,
      ReportCategory category) {
    var inventory = new SkuCapacity();
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
      if (productId.isPrometheusEnabled()) {
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
}
