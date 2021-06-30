package org.candlepin.subscriptions.subscription;

import lombok.Builder;
import org.candlepin.subscriptions.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Builder
public class SyncSubscriptionsTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(SyncSubscriptionsTask.class);

    private final SubscriptionSyncController subscriptionSyncController;
    private final String orgId;
    private final int offset;
    private final int limit;

    SyncSubscriptionsTask(SubscriptionSyncController subscriptionSyncController, String orgId,
                                 int offset, int limit) {
        this.subscriptionSyncController = subscriptionSyncController;
        this.orgId = orgId;
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public void execute() {
        log.info("Executing subscription sync for orgId={}, offset={}, limit={}",
                orgId, offset, limit);
        subscriptionSyncController.syncSubscriptions(orgId, offset, limit);
    }
}
