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

import static org.candlepin.subscriptions.resource.api.v1.CapacityResource.HYPERVISOR;
import static org.candlepin.subscriptions.resource.api.v1.CapacityResource.PHYSICAL;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CapacityReconciliationController {
  private static final String SOCKETS = "Sockets";
  private static final String CORES = "Cores";

  private final KafkaTemplate<String, ReconcileCapacityByOfferingTask>
      reconcileCapacityByOfferingKafkaTemplate;
  private final ProductDenylist productDenylist;

  private final Counter measurementsCreated;
  private final Counter measurementsUpdated;
  private final Counter measurementsDeleted;
  private final String reconcileCapacityTopic;
  private final SubscriptionRepository subscriptionRepository;

  @Autowired
  public CapacityReconciliationController(
      ProductDenylist productDenylist,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, ReconcileCapacityByOfferingTask>
          reconcileCapacityByOfferingKafkaTemplate,
      @Qualifier("reconcileCapacityTasks") TaskQueueProperties props,
      SubscriptionRepository subscriptionRepository) {
    this.productDenylist = productDenylist;
    this.reconcileCapacityByOfferingKafkaTemplate = reconcileCapacityByOfferingKafkaTemplate;
    this.reconcileCapacityTopic = props.getTopic();
    this.subscriptionRepository = subscriptionRepository;
    measurementsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_created");
    measurementsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_updated");
    measurementsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_deleted");
  }

  /** To be removed when SWATCH-2281 is done. */
  @Transactional
  public void reconcileCapacityForSubscription(Subscription subscription) {
    if (productDenylist.productIdMatches(subscription.getOffering().getSku())) {
      subscription.getSubscriptionMeasurements().clear();
      return;
    }

    reconcileSubscriptionCapacities(subscription);
  }

  /** To be removed when SWATCH-2280 is done. */
  public void enqueueReconcileCapacityForOffering(String sku) {
    var subscriptionCount = subscriptionRepository.countByOfferingSku(sku);
    var pageSize = 100;
    for (int i = 0; i < subscriptionCount; i += pageSize) {
      reconcileCapacityByOfferingKafkaTemplate.send(
          reconcileCapacityTopic,
          ReconcileCapacityByOfferingTask.builder().sku(sku).offset(i).limit(pageSize).build());
    }
  }

  private void reconcileSubscriptionCapacities(Subscription subscription) {
    Offering offering = subscription.getOffering();
    var existingKeys = new HashSet<>(subscription.getSubscriptionMeasurements().keySet());
    upsertMeasurement(subscription, offering.getCores(), PHYSICAL, CORES)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getHypervisorCores(), HYPERVISOR, CORES)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getSockets(), PHYSICAL, SOCKETS)
        .ifPresent(existingKeys::remove);
    upsertMeasurement(subscription, offering.getHypervisorSockets(), HYPERVISOR, SOCKETS)
        .ifPresent(existingKeys::remove);
    if (!offering.isMetered()) {
      // existingKeys now contains only stale SubscriptionMeasurement keys (i.e. measurements no
      // longer provided).
      existingKeys.forEach(subscription.getSubscriptionMeasurements()::remove);
      if (!existingKeys.isEmpty()) {
        measurementsDeleted.increment(existingKeys.size());
        log.info(
            "Update for subscription ID {} removed {} incorrect capacity measurements.",
            subscription.getSubscriptionId(),
            existingKeys.size());
      }
    }
  }

  private Optional<SubscriptionMeasurementKey> upsertMeasurement(
      Subscription subscription, Integer sourceValue, String measurementType, String metricId) {
    if (sourceValue != null && sourceValue > 0) {
      var key = new SubscriptionMeasurementKey();
      key.setMeasurementType(measurementType);
      key.setMetricId(metricId);
      Double existing = subscription.getSubscriptionMeasurements().get(key);
      var newValue = (double) (sourceValue * subscription.getQuantity());
      if (existing != null && !Objects.equals(existing, newValue)) {
        subscription.getSubscriptionMeasurements().put(key, newValue);
        measurementsUpdated.increment();
      } else if (existing == null) {
        subscription.getSubscriptionMeasurements().put(key, newValue);
        measurementsCreated.increment();
      }
      return Optional.of(key);
    }
    return Optional.empty();
  }
}
