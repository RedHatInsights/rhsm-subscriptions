package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@DirtiesContext
@Disabled
public class SubscriptionWorkerTest {

    @Autowired
    SubscriptionSyncController subscriptionSyncController;

    @Autowired
    SubscriptionWorker subscriptionWorker;

    @MockBean
    SubscriptionService subscriptionService;

    @Value("${test.topic}")
    private String topic;

    @Test
    public void givenKafkaDockerContainer_whenSendingtoSimpleProducer_thenMessageReceived()
            throws Exception {

        List<Subscription> subscriptions = List.of(new Subscription(), new Subscription(), new Subscription(), new Subscription(), new Subscription());
        Mockito.when(subscriptionService.getSubscriptionsByOrgId(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(subscriptions);

        subscriptionSyncController.syncSubscriptions("100", 0, 4);


    }
}
