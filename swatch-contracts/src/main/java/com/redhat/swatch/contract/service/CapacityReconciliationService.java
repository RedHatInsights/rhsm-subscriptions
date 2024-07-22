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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.config.ProductDenylist;
import com.redhat.swatch.contract.model.ReconcileCapacityByOfferingTask;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
@Slf4j
public class CapacityReconciliationService {
  public static final String PHYSICAL = "PHYSICAL";
  public static final String HYPERVISOR = "HYPERVISOR";
  public static final String SOCKETS = "Sockets";
  public static final String CORES = "Cores";

  private final SubscriptionRepository subscriptionRepository;
  private final MutinyEmitter<ReconcileCapacityByOfferingTask> reconcileCapacityByOfferingEmitter;
  private final ProductDenylist productDenylist;

  private final Counter measurementsCreated;
  private final Counter measurementsUpdated;
  private final Counter measurementsDeleted;

  @Inject
  public CapacityReconciliationService(
      SubscriptionRepository subscriptionRepository,
      ProductDenylist productDenylist,
      MeterRegistry meterRegistry,
      @Channel(Channels.CAPACITY_RECONCILE)
          MutinyEmitter<ReconcileCapacityByOfferingTask> reconcileCapacityByOfferingEmitter) {
    this.subscriptionRepository = subscriptionRepository;
    this.productDenylist = productDenylist;
    this.reconcileCapacityByOfferingEmitter = reconcileCapacityByOfferingEmitter;
    measurementsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_created");
    measurementsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_updated");
    measurementsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.measurements_deleted");
  }

  @Transactional
  public void reconcileCapacityForOffering(String sku, int offset, int limit) {
    List<SubscriptionEntity> subscriptions =
        subscriptionRepository.findByOfferingSku(sku, offset, limit);
    subscriptions.forEach(this::reconcileCapacityForSubscription);
    if (subscriptions.size() >= limit) {
      offset = offset + limit;
      // NOTE(khowell): we wait for the message send to be successful here so asynchronous send does
      // not propagate the transaction and cause confusing errors; see
      // https://github.com/quarkusio/quarkus/issues/21948#issuecomment-1068845737
      reconcileCapacityByOfferingEmitter.sendAndAwait(
          ReconcileCapacityByOfferingTask.builder().sku(sku).offset(offset).limit(limit).build());
    }
  }

  @Transactional
  public void reconcileCapacityForSubscription(SubscriptionEntity subscription) {
    if (productDenylist.productIdMatches(subscription.getOffering().getSku())) {
      subscription.getSubscriptionMeasurements().clear();
      return;
    }

    reconcileSubscriptionCapacities(subscription);
  }

  private void reconcileSubscriptionCapacities(SubscriptionEntity subscription) {
    OfferingEntity offering = subscription.getOffering();
    var existingKeys = new HashSet<>(subscription.getSubscriptionMeasurements());
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

  private Optional<SubscriptionMeasurementEntity> upsertMeasurement(
      SubscriptionEntity subscription,
      Integer sourceValue,
      String measurementType,
      String metricId) {
    if (sourceValue != null && sourceValue > 0) {
      var metric = new SubscriptionMeasurementEntity();
      metric.setMeasurementType(measurementType);
      metric.setMetricId(metricId);

      Optional<SubscriptionMeasurementEntity> existing =
          subscription.getSubscriptionMeasurement(metricId, measurementType);
      var newValue = (double) (sourceValue * subscription.getQuantity());
      if (existing.isPresent() && !Objects.equals(existing.get().getValue(), newValue)) {
        existing.get().setValue(newValue);
        measurementsUpdated.increment();
      } else if (existing.isEmpty()) {
        SubscriptionMeasurementEntity newMetric = new SubscriptionMeasurementEntity();
        newMetric.setMeasurementType(measurementType);
        newMetric.setMetricId(metricId);
        newMetric.setValue(newValue);
        newMetric.setSubscription(subscription);
        subscription.getSubscriptionMeasurements().add(newMetric);
        measurementsCreated.increment();
      }

      return existing;
    }
    return Optional.empty();
  }
}
