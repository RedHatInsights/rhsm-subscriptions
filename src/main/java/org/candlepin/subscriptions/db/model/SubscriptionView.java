package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;

public interface SubscriptionView {

    String getSku();

    OffsetDateTime getEndDate();

    OffsetDateTime getBeginDate();

    int getPhysicalSockets();

    int getPhysicalCores();

    int getVirtualSockets();

    int getVirtualCores();
}
