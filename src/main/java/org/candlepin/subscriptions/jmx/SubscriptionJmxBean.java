package org.candlepin.subscriptions.jmx;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
@Slf4j
public class SubscriptionJmxBean {

    SubscriptionSyncController subscriptionSyncController;

    SubscriptionJmxBean(SubscriptionSyncController subscriptionSyncController){
        this.subscriptionSyncController = subscriptionSyncController;
    }

    @ManagedOperation
    void syncSubscription(String subscriptionId){
        Object principal = ResourceUtils.getPrincipal();
        log.info("Sync for subscription {} triggered over JMX by {}", subscriptionId, principal);
        subscriptionSyncController.syncSubscription(subscriptionId);
    }

    @ManagedOperation
    void syncSubscriptionsForOrg(String orgId){
        Object principal = ResourceUtils.getPrincipal();
        log.info("Sync for org {} triggered over JMX by {}", orgId, principal);
        subscriptionSyncController.syncSubscriptions(orgId, 0, 100);
    }
}
