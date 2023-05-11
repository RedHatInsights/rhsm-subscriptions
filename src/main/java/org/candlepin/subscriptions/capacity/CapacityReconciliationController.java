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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CapacityReconciliationController {

  private final OfferingRepository offeringRepository;
  private final SubscriptionRepository subscriptionRepository;
  private KafkaTemplate<String, ReconcileCapacityByOfferingTask>
      reconcileCapacityByOfferingKafkaTemplate;
  private final ProductDenylist productDenylist;
  private final CapacityProductExtractor productExtractor;
  private final SubscriptionCapacityRepository subscriptionCapacityRepository;

  private final Counter capacityRecordsCreated;
  private final Counter capacityRecordsUpdated;
  private final Counter capacityRecordsDeleted;
  private String reconcileCapacityTopic;

  @Autowired
  public CapacityReconciliationController(
      OfferingRepository offeringRepository,
      SubscriptionRepository subscriptionRepository,
      ProductDenylist productDenylist,
      CapacityProductExtractor productExtractor,
      SubscriptionCapacityRepository subscriptionCapacityRepository,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, ReconcileCapacityByOfferingTask>
          reconcileCapacityByOfferingKafkaTemplate,
      @Qualifier("reconcileCapacityTasks") TaskQueueProperties props) {
    this.offeringRepository = offeringRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.productDenylist = productDenylist;
    this.productExtractor = productExtractor;
    this.subscriptionCapacityRepository = subscriptionCapacityRepository;
    this.reconcileCapacityByOfferingKafkaTemplate = reconcileCapacityByOfferingKafkaTemplate;
    this.reconcileCapacityTopic = props.getTopic();
    capacityRecordsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.records_created");
    capacityRecordsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.records_updated");
    capacityRecordsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.records_deleted");
  }

  @Transactional
  public void reconcileCapacityForSubscription(Subscription subscription) {

    Collection<SubscriptionCapacity> newCapacities = mapSubscriptionToCapacities(subscription);
    reconcileSubscriptionCapacities(
        newCapacities,
        subscription.getOrgId(),
        subscription.getSubscriptionId(),
        subscription.getSku());
  }

  @Transactional
  public void reconcileCapacityForOffering(String sku, int offset, int limit) {

    Page<Subscription> subscriptions =
        subscriptionRepository.findBySku(
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

  private Collection<SubscriptionCapacity> mapSubscriptionToCapacities(Subscription subscription) {
    var optionalOffering = offeringRepository.findById(subscription.getSku());
    if (optionalOffering.isEmpty()) {
      return Collections.emptyList();
    }

    var offering = optionalOffering.get();
    Set<String> products = productExtractor.getProducts(offering);
    return products.stream()
        .map(product -> from(subscription, offering, product))
        .collect(Collectors.toList());
  }

  private void reconcileSubscriptionCapacities(
      Collection<SubscriptionCapacity> newCapacities,
      String orgId,
      String subscriptionId,
      String sku) {

    Collection<SubscriptionCapacity> toSave = new ArrayList<>();
    Map<SubscriptionCapacityKey, SubscriptionCapacity> existingCapacityMap =
        subscriptionCapacityRepository
            .findByKeyOrgIdAndKeySubscriptionIdIn(orgId, Collections.singletonList(subscriptionId))
            .stream()
            .collect(Collectors.toMap(SubscriptionCapacity::getKey, Function.identity()));

    if (!productDenylist.productIdMatches(sku)) {
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
