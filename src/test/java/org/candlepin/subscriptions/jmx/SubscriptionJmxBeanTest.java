package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.IntStream;

@ExtendWith(MockitoExtension.class)
class SubscriptionJmxBeanTest {

    @Mock
    private SubscriptionSyncController subscriptionSyncController;

    @Mock
    private OrgConfigRepository orgConfigRepository;

    private SubscriptionJmxBean subject;

    @BeforeEach
    void setup() {
        subject = new SubscriptionJmxBean(subscriptionSyncController, orgConfigRepository);
    }

    @Test
    void syncAllSubscriptionsTest()  {
        Mockito.when(orgConfigRepository.findSyncEnabledOrgs()).thenReturn(IntStream.range(1, 10).mapToObj(
                String::valueOf));
        subject.syncAllSubscriptions();
        Mockito.verify(subscriptionSyncController, Mockito.times(9))
                .syncAllSubcriptionsForOrg(Mockito.anyString());
    }

    @Test
    void syncSubscriptionForOrgTest(){
        subject.syncSubscriptionsForOrg("123");
        Mockito.verify(subscriptionSyncController).syncAllSubcriptionsForOrg("123");
    }

    @Test
    void syncSubscriptionTest() {
        subject.syncSubscription("testid");
        Mockito.verify(subscriptionSyncController).syncSubscription("testid");
    }
}
