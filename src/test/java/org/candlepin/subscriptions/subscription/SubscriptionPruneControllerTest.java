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
package org.candlepin.subscriptions.subscription;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.stream.Stream;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class SubscriptionPruneControllerTest {
  private final SubscriptionRepository subscriptionRepo;
  private final SubscriptionCapacityRepository capacityRepo;
  private final OrgConfigRepository orgConfigRepo;
  private final KafkaTemplate<String, PruneSubscriptionsTask> kafkaTemplate;
  private final SubscriptionPruneController controller;

  SubscriptionPruneControllerTest(
      @Mock SubscriptionRepository subscriptionRepo,
      @Mock SubscriptionCapacityRepository capacityRepo,
      @Mock OrgConfigRepository orgConfigRepo,
      @Mock MeterRegistry meterRegistry,
      @Mock Timer timer,
      @Mock KafkaTemplate<String, PruneSubscriptionsTask> kafkaTemplate,
      @Mock ProductWhitelist allowList) {
    this.subscriptionRepo = subscriptionRepo;
    this.capacityRepo = capacityRepo;
    this.orgConfigRepo = orgConfigRepo;
    this.kafkaTemplate = kafkaTemplate;
    TaskQueueProperties queueProperties = new TaskQueueProperties();
    when(meterRegistry.timer(any())).thenReturn(timer);
    when(allowList.productIdMatches("allowed")).thenReturn(true);
    when(allowList.productIdMatches("denied")).thenReturn(false);
    controller =
        new SubscriptionPruneController(
            subscriptionRepo,
            capacityRepo,
            orgConfigRepo,
            meterRegistry,
            kafkaTemplate,
            allowList,
            queueProperties);
  }

  @Test
  void testPruneUnlistedOnlyEnqueuesWork() {
    when(orgConfigRepo.findSyncEnabledOrgs()).thenReturn(Stream.of("org1", "org2"));
    controller.pruneAllUnlistedSubscriptions();
    verify(kafkaTemplate, times(2)).send(any(), any());
    verifyNoInteractions(subscriptionRepo, capacityRepo);
  }

  @Test
  void testPruneDoesNothingIfSkuOnAllowlist() {
    Subscription allowedSub = new Subscription();
    allowedSub.setSku("allowed");
    when(subscriptionRepo.findByOwnerId("up-to-date")).thenReturn(Stream.of(allowedSub));
    SubscriptionCapacity allowedCapacity = new SubscriptionCapacity();
    allowedCapacity.setSku("allowed");
    when(capacityRepo.findByKeyOwnerId("up-to-date")).thenReturn(Stream.of(allowedCapacity));
    controller.pruneUnlistedSubscriptions("up-to-date");
    verify(subscriptionRepo).findByOwnerId("up-to-date");
    verify(capacityRepo).findByKeyOwnerId("up-to-date");
    verifyNoMoreInteractions(subscriptionRepo, capacityRepo);
  }

  @Test
  void testPruneRemovesDelistedCapacity() {
    when(subscriptionRepo.findByOwnerId("stale-capacity")).thenReturn(Stream.of());
    SubscriptionCapacity staleCapacity = new SubscriptionCapacity();
    staleCapacity.setSku("denied");
    when(capacityRepo.findByKeyOwnerId("stale-capacity")).thenReturn(Stream.of(staleCapacity));
    controller.pruneUnlistedSubscriptions("stale-capacity");
    verify(subscriptionRepo).findByOwnerId("stale-capacity");
    verify(capacityRepo).findByKeyOwnerId("stale-capacity");
    verify(capacityRepo).delete(staleCapacity);
    verifyNoMoreInteractions(subscriptionRepo);
  }

  @Test
  void testPruneRemovesDelistedSubscription() {
    Subscription staleSub = new Subscription();
    staleSub.setSku("denied");
    when(subscriptionRepo.findByOwnerId("stale-sub")).thenReturn(Stream.of(staleSub));
    when(capacityRepo.findByKeyOwnerId("stale-sub")).thenReturn(Stream.of());
    controller.pruneUnlistedSubscriptions("stale-sub");
    verify(subscriptionRepo).findByOwnerId("stale-sub");
    verify(capacityRepo).findByKeyOwnerId("stale-sub");
    verify(subscriptionRepo).delete(staleSub);
    verifyNoMoreInteractions(capacityRepo);
  }
}
