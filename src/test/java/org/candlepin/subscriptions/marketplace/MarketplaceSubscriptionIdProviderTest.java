/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles({"marketplace", "test"})
class MarketplaceSubscriptionIdProviderTest {

    @MockBean
    private SubscriptionRepository repo;

    @MockBean
    private SubscriptionSyncController syncController;

    @MockBean
    private MarketplaceSubscriptionCollector collector;

    @Autowired
    private MarketplaceSubscriptionIdProvider idProvider;

    private OffsetDateTime rangeStart = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime rangeEnd = OffsetDateTime.MAX;

    @Test
    void doesNotAllowReservedValuesInKey() {
        UsageCalculation.Key key1 = new Key(String.valueOf(1), ServiceLevel._ANY, Usage.PRODUCTION);
        UsageCalculation.Key key2 = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage._ANY);

        assertThrows(IllegalArgumentException.class, () -> idProvider.findSubscriptionId("1000", key1,
            rangeStart, rangeEnd));
        assertThrows(IllegalArgumentException.class, () -> idProvider.findSubscriptionId("1000", key2,
            rangeStart, rangeEnd));
    }

    @Test
    void findsSubscriptionId() {
        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Subscription s = new Subscription();
        s.setStartDate(OffsetDateTime.now());
        s.setEndDate(OffsetDateTime.now().plusDays(7));
        s.setMarketplaceSubscriptionId("xyz");
        List<Subscription> result = Collections.singletonList(s);

        when(repo.findSubscriptionByAccountAndUsageKey("1000", key)).thenReturn(result);

        Optional<Object> actual = idProvider.findSubscriptionId("1000", key, rangeStart, rangeEnd);
        assertEquals("xyz", actual.get());
    }

    @Test
    void memoizesSubscriptionId() {
        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Subscription s = new Subscription();
        s.setStartDate(OffsetDateTime.now());
        s.setEndDate(OffsetDateTime.now().plusDays(7));
        s.setMarketplaceSubscriptionId("abc");
        List<Subscription> result = Collections.singletonList(s);

        when(repo.findSubscriptionByAccountAndUsageKey("1000", key))
            .thenReturn(new ArrayList<>())
            .thenReturn(result);

        Optional<Object> actual = idProvider.findSubscriptionId("1000", key, rangeStart, rangeEnd);
        assertEquals("abc", actual.get());
        verify(collector).fetchSubscription("1000", key);
    }
}
