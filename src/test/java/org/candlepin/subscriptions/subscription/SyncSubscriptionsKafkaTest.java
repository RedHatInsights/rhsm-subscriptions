package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.kafka.KafkaTaskQueueTester;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"platform.rhsm-subscriptions.sync"},
        brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" })
@ActiveProfiles({"worker","test", "kafka-test"})
public class SyncSubscriptionsKafkaTest{

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Autowired private SubscriptionSyncController subscriptionSyncController;

    @Autowired
    @Qualifier("subscriptionTasks")
    private TaskQueueProperties taskQueueProperties;

    @Autowired private ApplicationClock clock;

    @MockBean
    SubscriptionRepository subscriptionRepository;

    @MockBean
    CapacityReconciliationController capacityReconciliationController;

    @MockBean
    SubscriptionService subscriptionService;

    @Test
    void testSendAndReceiveTaskMessage() throws InterruptedException {
        runSendAndReceiveTaskMessageTest();
    }

    protected void runSendAndReceiveTaskMessageTest() throws InterruptedException {

        // Expect the task to be ran once.
        CountDownLatch latch = new CountDownLatch(1);
        CountDownTask cdt = new CountDownTask(latch);

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
