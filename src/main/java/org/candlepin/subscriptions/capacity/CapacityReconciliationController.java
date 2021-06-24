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

import static org.candlepin.subscriptions.db.model.SubscriptionCapacity.from;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.*;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CapacityReconciliationController {

  private final OfferingRepository offeringRepository;
  private final ProductWhitelist productWhitelist;
  private final CapacityProductExtractor productExtractor;
  private final SubscriptionCapacityRepository subscriptionCapacityRepository;
  private final Counter capacityRecordsCreated;
  private final Counter capacityRecordsUpdated;
  private final Counter capacityRecordsDeleted;

  public CapacityReconciliationController(
      OfferingRepository offeringRepository,
      ProductWhitelist productWhitelist,
      CapacityProductExtractor productExtractor,
      SubscriptionCapacityRepository subscriptionCapacityRepository,
      MeterRegistry meterRegistry) {
    this.offeringRepository = offeringRepository;
    this.productWhitelist = productWhitelist;
    this.productExtractor = productExtractor;
    this.subscriptionCapacityRepository = subscriptionCapacityRepository;
    capacityRecordsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.records_created");
    capacityRecordsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.records_updated");
    capacityRecordsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.records_deleted");
  }

  public void reconcileCapacityForSubscription(Subscription subscription) {

    Collection<SubscriptionCapacity> newCapacities = mapSubscriptionToCapacities(subscription);
    reconcileSubscriptionCapacities(
        newCapacities, subscription.getSubscriptionId(), subscription.getSku());
  }

  private Collection<SubscriptionCapacity> mapSubscriptionToCapacities(Subscription subscription) {

    Offering offering = offeringRepository.getById(subscription.getSku());
    Set<String> products =
        productExtractor.getProducts(
            offering.getProductIds().stream().map(Object::toString).collect(Collectors.toSet()));

    return products.stream()
        .map(product -> from(subscription, offering, product))
        .collect(Collectors.toList());
  }

  private void reconcileSubscriptionCapacities(
      Collection<SubscriptionCapacity> newCapacities, String subscriptionId, String sku) {

    Collection<SubscriptionCapacity> toSave = new ArrayList<>();
    Map<SubscriptionCapacityKey, SubscriptionCapacity> existingCapacityMap =
        subscriptionCapacityRepository.findByKeySubscriptionId(subscriptionId).stream()
            .collect(Collectors.toMap(SubscriptionCapacity::getKey, Function.identity()));

    if (productWhitelist.productIdMatches(sku)) {
      newCapacities.forEach(
          newCapacity -> {
            toSave.add(newCapacity);
            SubscriptionCapacity oldVersion = existingCapacityMap.remove(newCapacity.getKey());
            if (oldVersion != null) {
              capacityRecordsUpdated.increment();
            } else {
              capacityRecordsCreated.increment();
            }
          });
      subscriptionCapacityRepository.saveAll(toSave);
    }

    Collection<SubscriptionCapacity> toDelete = new ArrayList<>(existingCapacityMap.values());
    subscriptionCapacityRepository.deleteAll(toDelete);
    if (!toDelete.isEmpty()) {
      log.info(
          "Update for subscription ID {} removed {} incorrect capacity records.",
          subscriptionId,
          toDelete.size());
    }
    capacityRecordsDeleted.increment(toDelete.size());
  }
}
