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
package org.candlepin.subscriptions.task.queue.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.subscription.SubscriptionDtoUtil;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.tally.TallyTaskFactory;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Base class for testing message sending and receiving via Kafka. */
public class KafkaTaskQueueTester {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @MockBean private TallyTaskFactory factory;

  @Autowired private CaptureSnapshotsTaskManager manager;

  @Autowired private TaskQueueProperties taskQueueProperties;

  @Autowired private SubscriptionSyncController subscriptionSyncController;

  @Autowired
  @Qualifier("subscriptionTasks")
  private TaskQueueProperties subscriptionTaskQueueProperties;

  @Autowired private ApplicationClock clock;

  @MockBean
  SubscriptionRepository subscriptionRepository;

  @MockBean
  CapacityReconciliationController capacityReconciliationController;

  @MockBean
  SubscriptionService subscriptionService;

  protected void runSendAndReceiveTaskMessageTest() throws InterruptedException {
    String account = "12345";
    TaskDescriptor taskDescriptor =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic())
            .setSingleValuedArg("accounts", account)
            .build();

    // Expect the task to be ran once.
    CountDownLatch latch = new CountDownLatch(1);
    CountDownTask cdt = new CountDownTask(latch);

    when(factory.build(eq(taskDescriptor))).thenReturn(cdt);

    manager.updateAccountSnapshots(account);

    // Wait a max of 5 seconds for the task to be executed
    latch.await(5L, TimeUnit.SECONDS);
    assertTrue(cdt.taskWasExecuted(), "The task failed to execute. The message was not received.");
  }

  protected void runSendAndReceiveSubscriptionSyncTaskMessageTest() throws InterruptedException {

    // Expect the task to be ran once.
    CountDownLatch latch = new CountDownLatch(1);
    KafkaTaskQueueTester.CountDownTask cdt = new KafkaTaskQueueTester.CountDownTask(latch);

    List<Subscription> subscriptions = List.of(
            createDto(100,  "456",10),
            createDto(100,  "457",10),
            createDto(100,  "458",10),
            createDto(100,  "459",10),
            createDto(100,  "500",10));

    Mockito.when(subscriptionService.getSubscriptionsByOrgId(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(subscriptions);
    subscriptions.forEach(subscription -> {
      Mockito.when(subscriptionRepository.findActiveSubscription(subscription.getId().toString())).thenReturn(Optional.of(convertDto(subscription)));
    });

    subscriptionSyncController.syncSubscriptions("100", 0, 4);

    // Wait a max of 5 seconds for the task to be executed
    latch.await(5L, TimeUnit.SECONDS);
    assertTrue(cdt.taskWasExecuted(), "The task failed to execute. The message was not received.");
  }

  /**
   * A testing Task that uses a latch to allow the calling test to know that it has been executed.
   * It provides an executed field to allow tests to verify that the Task has actually been run in
   * cases where latch.await(timeout) times out waiting for it to execute.
   */
  private class CountDownTask implements Task {

    private CountDownLatch latch;
    private boolean executed;

    public CountDownTask(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void execute() {
      executed = true;
      latch.countDown();
    }

    public boolean taskWasExecuted() {
      return executed;
    }
  }

  private org.candlepin.subscriptions.subscription.api.model.Subscription createDto(
          Integer orgId, String subId, int quantity) {
    final var dto = new org.candlepin.subscriptions.subscription.api.model.Subscription();
    dto.setQuantity(quantity);
    dto.setId(Integer.valueOf(subId));
    dto.setSubscriptionNumber("123");
    dto.setEffectiveStartDate(NOW.toEpochSecond());
    dto.setEffectiveEndDate(NOW.plusDays(30).toEpochSecond());
    dto.setWebCustomerId(orgId);

    var product = new SubscriptionProduct().parentSubscriptionProductId(null).sku("testsku");
    List<SubscriptionProduct> products = Collections.singletonList(product);
    dto.setSubscriptionProducts(products);

    return dto;
  }


  private org.candlepin.subscriptions.db.model.Subscription convertDto(org.candlepin.subscriptions.subscription.api.model.Subscription subscription) {

    return org.candlepin.subscriptions.db.model.Subscription.builder()
            .subscriptionId(String.valueOf(subscription.getId()))
            .sku(SubscriptionDtoUtil.extractSku(subscription))
            .ownerId(subscription.getWebCustomerId().toString())
            .accountNumber(String.valueOf(subscription.getOracleAccountNumber()))
            .quantity(subscription.getQuantity())
            .startDate(clock.dateFromMilliseconds(subscription.getEffectiveStartDate()))
            .endDate(clock.dateFromMilliseconds(subscription.getEffectiveEndDate()))
            .marketplaceSubscriptionId(SubscriptionDtoUtil.extractMarketplaceId(subscription))
            .build();
  }
}
