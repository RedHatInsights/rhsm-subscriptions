package org.candlepin.subscriptions.subscription;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Builder
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@ToString
public class SyncSubscriptions {
    private static final Logger log = LoggerFactory.getLogger(SyncSubscriptions.class);

    private String orgId;
    private int offset;
    private int limit;

}
