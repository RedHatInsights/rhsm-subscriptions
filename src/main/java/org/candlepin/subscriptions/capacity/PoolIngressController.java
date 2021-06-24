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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Controller for ingesting subscription information from Candlepin pools. */
@Component
public class PoolIngressController {

  private static final Logger log = LoggerFactory.getLogger(PoolIngressController.class);

  private final SubscriptionCapacityRepository subscriptionCapacityRepository;
  private final CandlepinPoolCapacityMapper capacityMapper;
  private final ProductWhitelist productWhitelist;
  private final Counter poolsProcessed;
  private final Counter poolsWhitelisted;
  private final Counter capacityRecordsCreated;
  private final Counter capacityRecordsUpdated;
  private final Counter capacityRecordsDeleted;
  private boolean syncFromSubscriptionService;
  private final SubscriptionSyncController subscriptionSyncController;

  public PoolIngressController(
      SubscriptionCapacityRepository subscriptionCapacityRepository,
      CandlepinPoolCapacityMapper capacityMapper,
      ProductWhitelist productWhitelist,
      MeterRegistry meterRegistry,
      SubscriptionSyncController subscriptionSyncController) {

    this.subscriptionCapacityRepository = subscriptionCapacityRepository;
    this.capacityMapper = capacityMapper;
    this.productWhitelist = productWhitelist;
    poolsProcessed = meterRegistry.counter("rhsm-subscriptions.capacity.pools");
    poolsWhitelisted = meterRegistry.counter("rhsm-subscriptions.capacity.whitelisted_pools");
    capacityRecordsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.records_created");
    capacityRecordsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.records_updated");
    capacityRecordsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.records_deleted");
    this.subscriptionSyncController = subscriptionSyncController;
  }

  @Transactional
  @Timed("rhsm-subscriptions.capacity.ingress")
  public void updateCapacityForOrg(String orgId, List<CandlepinPool> pools) {
    if (syncFromSubscriptionService) {
      updateSubscriptionsAndCapacityFromSubscriptions(orgId, pools);
    } else {
      updateCapacityFromPools(orgId, pools);
    }
  }

  @Timed("rhsm-subscriptions.subscription.ingress")
  public void updateSubscriptionsAndCapacityFromSubscriptions(
      String orgId, List<CandlepinPool> pools) {
    final List<String> subscriptionIds =
        pools.stream().map(CandlepinPool::getSubscriptionId).collect(Collectors.toList());
    subscriptionIds.forEach(subscriptionSyncController::syncSubscription);
  }

  @Timed("rhsm-subscriptions.capacity.ingress")
  public void updateCapacityFromPools(String orgId, List<CandlepinPool> pools) {
    List<String> subscriptionIds =
        pools.stream().map(CandlepinPool::getSubscriptionId).collect(Collectors.toList());

    Collection<SubscriptionCapacity> existingCapacityRecords =
        subscriptionCapacityRepository.findByKeyOwnerIdAndKeySubscriptionIdIn(
            orgId, subscriptionIds);

    // used to lookup existing capacity records by key, per subscription ID
    Map<String, Map<SubscriptionCapacityKey, SubscriptionCapacity>> subscriptionCapacityMaps =
        new HashMap<>();

    for (SubscriptionCapacity capacity : existingCapacityRecords) {
      String subscriptionId = capacity.getSubscriptionId();
      Map<SubscriptionCapacityKey, SubscriptionCapacity> subscriptionCapacityMap =
          subscriptionCapacityMaps.computeIfAbsent(subscriptionId, s -> new HashMap<>());
      subscriptionCapacityMap.put(capacity.getKey(), capacity);
    }

    Collection<SubscriptionCapacity> needsSave = new ArrayList<>();
    Collection<SubscriptionCapacity> needsDelete = new ArrayList<>();
    int whiteListedPoolCount = 0;
    for (CandlepinPool pool : pools) {
      Map<SubscriptionCapacityKey, SubscriptionCapacity> subscriptionCapacityMap =
          subscriptionCapacityMaps.getOrDefault(pool.getSubscriptionId(), Collections.emptyMap());
      if (productWhitelist.productIdMatches(pool.getProductId())) {
        whiteListedPoolCount++;

        Collection<SubscriptionCapacity> modifiedPoolCapacity =
            capacityMapper.mapPoolToSubscriptionCapacity(orgId, pool, subscriptionCapacityMap);

        modifiedPoolCapacity.forEach(
            capacity -> {
              needsSave.add(capacity);
              SubscriptionCapacityKey key = capacity.getKey();
              SubscriptionCapacity oldVersion = subscriptionCapacityMap.remove(key);
              if (oldVersion != null) {
                capacityRecordsUpdated.increment();
              } else {
                capacityRecordsCreated.increment();
              }
            });
      }
      // at this point anything left in the subscription capacity map must be stale; needs deletion
      needsDelete.addAll(subscriptionCapacityMap.values());
    }

    subscriptionCapacityRepository.saveAll(needsSave);
    subscriptionCapacityRepository.deleteAll(needsDelete);

    log.info(
        "Update for org {} processed {} of {} posted pools, resulting in {} capacity records.",
        orgId,
        whiteListedPoolCount,
        pools.size(),
        needsSave.size());
    poolsWhitelisted.increment(whiteListedPoolCount);
    poolsProcessed.increment(pools.size());

    if (!needsDelete.isEmpty()) {
      log.info(
          "Update for org {} removed {} incorrect capacity records.", orgId, needsDelete.size());
    }
    capacityRecordsDeleted.increment(needsDelete.size());
  }
}
