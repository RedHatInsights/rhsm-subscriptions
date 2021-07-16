package org.candlepin.subscriptions.subscription;

import lombok.*;
import org.candlepin.subscriptions.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Builder
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class SyncSubscriptionsTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(SyncSubscriptionsTask.class);

    private SubscriptionSyncController subscriptionSyncController;
    private String orgId;
    private int offset;
    private int limit;

    @Override
    public String toString() {
        return "SyncSubscriptionsTask{" +
                "subscriptionSyncController=" + subscriptionSyncController +
                ", orgId='" + orgId + '\'' +
                ", offset=" + offset +
                ", limit=" + limit +
                '}';
    }

    @Override
    public void execute() {
        log.info("Executing subscription sync for orgId={}, offset={}, limit={}",
                orgId, offset, limit);
       // subscriptionSyncController.syncSubscriptions(orgId, offset, limit);
    }
}
