package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
public class SubscriptionJmxBean {

    SubscriptionSyncController subscriptionSyncController;

    SubscriptionJmxBean(SubscriptionSyncController subscriptionSyncController){
        this.subscriptionSyncController = subscriptionSyncController;
    }

    @ManagedOperation
    void syncSubscription(String subscriptionId){
        subscriptionSyncController.syncSubscription(subscriptionId);
    }
    
}
