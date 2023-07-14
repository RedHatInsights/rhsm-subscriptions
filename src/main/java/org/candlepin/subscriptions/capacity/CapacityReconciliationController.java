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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Subscription.SubscriptionCompoundId;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.SubscriptionProductId;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CapacityReconciliationController {
  private static final String SOCKETS = MetricId.SOCKETS.toString().toUpperCase();
  private static final String CORES = MetricId.CORES.toString().toUpperCase();

  private final SubscriptionRepository subscriptionRepository;
  private final KafkaTemplate<String, ReconcileCapacityByOfferingTask>
      reconcileCapacityByOfferingKafkaTemplate;
  private final ProductDenylist productDenylist;
  private final CapacityProductExtractor productExtractor;

  private final Counter measurementsCreated;
  private final Counter measurementsUpdated;
  private final Counter measurementsDeleted;
  private final String reconcileCapacityTopic;

  @Autowired
  public CapacityReconciliationController(
      SubscriptionRepository subscriptionRepository,
      ProductDenylist productDenylist,
      CapacityProductExtractor productExtractor,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, ReconcileCapacityByOfferingTask>
          reconcileCapacityByOfferingKafkaTemplate,
      @Qualifier("reconcileCapacityTasks") TaskQueueProperties props) {
    this.subscriptionRepository = subscriptionRepository;
    this.productDenylist = productDenylist;
    this.productExtractor = productExtractor;
    this.reconcileCapacityByOfferingKafkaTemplate = reconcileCapacityByOfferingKafkaTemplate;
    this.reconcileCapacityTopic = props.getTopic();
    measurementsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_created");
    measurementsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_updated");
    measurementsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_deleted");
  }

  @Transactional
  public void reconcileCapacityForSubscription(Subscription subscription) {
    if (productDenylist.productIdMatches(subscription.getOffering().getSku())) {
      subscription.getSubscriptionMeasurements().clear();
      subscription.getSubscriptionProductIds().clear();
      return;
    }

    reconcileSubscriptionCapacities(subscription);
    reconcileSubscriptionProductIds(subscription);
  }

  @Transactional
  public void reconcileCapacityForOffering(String sku, int offset, int limit) {
    Page<Subscription> subscriptions =
        subscriptionRepository.findByOfferingSku(
            sku, ResourceUtils.getPageable(offset, limit, Sort.by("subscriptionId")));
    subscriptions.forEach(this::reconcileCapacityForSubscription);
    if (subscriptions.hasNext()) {
      offset = offset + limit;
      reconcileCapacityByOfferingKafkaTemplate.send(
          reconcileCapacityTopic,
          ReconcileCapacityByOfferingTask.builder().sku(sku).offset(offset).limit(limit).build());
    }
  }

  public void enqueueReconcileCapacityForOffering(String sku) {
    reconcileCapacityByOfferingKafkaTemplate.send(
        reconcileCapacityTopic,
        ReconcileCapacityByOfferingTask.builder().sku(sku).offset(0).limit(100).build());
  }

  private void reconcileSubscriptionCapacities(Subscription subscription) {
    Offering offering = subscription.getOffering();
    var existingKeys =
        subscription.getSubscriptionMeasurements().stream()
            .map(m -> fromSubscriptionAndMeasurement(subscription, m))
            .collect(Collectors.toCollection(HashSet::new));
    upsertMeasurement(subscription, offering.getCores(), "PHYSICAL", CORES)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getHypervisorCores(), "HYPERVISOR", CORES)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getSockets(), "PHYSICAL", SOCKETS)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getHypervisorSockets(), "HYPERVISOR", SOCKETS)
        .ifPresent(existingKeys::remove);
    // existingKeys now contains only stale SubscriptionMeasurement keys (i.e. measurements no
    // longer provided).
    existingKeys.forEach(
        key ->
            subscription
                .getSubscriptionMeasurements()
                .removeIf(
                    m -> Objects.equals(fromSubscriptionAndMeasurement(subscription, m), key)));
    if (!existingKeys.isEmpty()) {
      measurementsDeleted.increment(existingKeys.size());
      log.info(
          "Update for subscription ID {} removed {} incorrect capacity measurements.",
          subscription.getSubscriptionId(),
          existingKeys.size());
    }
  }

  private SubscriptionMeasurementKey fromSubscriptionAndMeasurement(
      Subscription subscription, SubscriptionMeasurement measurement) {
    return new SubscriptionMeasurementKey(
        new SubscriptionCompoundId(subscription.getSubscriptionId(), subscription.getStartDate()),
        measurement.getMetricId(),
        measurement.getMeasurementType());
  }

  private Optional<SubscriptionMeasurementKey> upsertMeasurement(
      Subscription subscription, Integer sourceValue, String measurementType, String metricId) {
    if (sourceValue != null && sourceValue > 0) {
      var measurement = new SubscriptionMeasurement();
      // subscription set here so that comparison works as expected
      measurement.setSubscription(subscription);
      measurement.setMeasurementType(measurementType);
      measurement.setMetricId(metricId);
      measurement.setValue((double) (sourceValue * subscription.getQuantity()));
      var key = fromSubscriptionAndMeasurement(subscription, measurement);
      var existingMeasurement =
          subscription.getSubscriptionMeasurements().stream()
              .filter(m -> Objects.equals(fromSubscriptionAndMeasurement(subscription, m), key))
              .findFirst();
      if (existingMeasurement.isPresent()
          && !Objects.equals(existingMeasurement.get().getValue(), measurement.getValue())) {
        existingMeasurement.get().setValue(measurement.getValue());
        measurementsUpdated.increment();
      } else {
        subscription.addSubscriptionMeasurement(measurement);
        measurementsCreated.increment();
      }
      return Optional.of(key);
    }
    return Optional.empty();
  }

  private void reconcileSubscriptionProductIds(Subscription subscription) {
    Offering offering = subscription.getOffering();

    Set<String> products = productExtractor.getProducts(offering.getProductIdsAsStrings());
    var expectedProducts =
        products.stream()
            .map(
                product -> {
                  var id = new SubscriptionProductId();
                  // subscription set here so that comparison works as expected
                  id.setSubscription(subscription);
                  id.setProductId(product);
                  return id;
                })
            .collect(Collectors.toSet());
    var toBeRemoved =
        subscription.getSubscriptionProductIds().stream()
            .filter(p -> !expectedProducts.contains(p))
            .collect(Collectors.toSet());
    subscription.getSubscriptionProductIds().removeAll(toBeRemoved);
    expectedProducts.forEach(subscription::addSubscriptionProductId);
    if (!toBeRemoved.isEmpty()) {
      log.info(
          "Update for subscription ID {} removed {} products.",
          subscription.getSubscriptionId(),
          toBeRemoved.size());
    }
  }
}
