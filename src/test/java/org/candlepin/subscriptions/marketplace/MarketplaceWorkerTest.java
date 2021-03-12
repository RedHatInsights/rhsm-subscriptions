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

import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.marketplace.MarketplaceWorker.MarketplacePayloadMapper;
import org.candlepin.subscriptions.marketplace.MarketplaceWorker.MarketplaceProducer;
import org.candlepin.subscriptions.marketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MarketplaceWorkerTest {

    @Test
    void testWorkerCallsProduceForNonEmptyPayload() {
        ApplicationProperties properties = new ApplicationProperties();
        MarketplaceProducer producer = mock(MarketplaceProducer.class);
        MarketplacePayloadMapper payloadMapper = mock(MarketplacePayloadMapper.class);
        var worker = new MarketplaceWorker(properties, producer, payloadMapper);

        UsageRequest usageRequest = new UsageRequest().data(List.of(new UsageEvent()));
        when(payloadMapper.mapTallySummary(any())).thenReturn(usageRequest);

        worker.receive(new TallySummary());

        verify(producer, times(1)).submitUsageRequest(usageRequest);
    }

    @Test
    void testWorkerSkipsEmptyPayloads() {
        ApplicationProperties properties = new ApplicationProperties();
        MarketplaceProducer producer = mock(MarketplaceProducer.class);
        MarketplacePayloadMapper payloadMapper = mock(MarketplacePayloadMapper.class);
        var worker = new MarketplaceWorker(properties, producer, payloadMapper);

        UsageRequest usageRequest = new UsageRequest().data(Collections.emptyList());
        when(payloadMapper.mapTallySummary(any())).thenReturn(usageRequest);

        worker.receive(new TallySummary());

        verify(producer, times(0)).submitUsageRequest(any());
    }

    @Test
    void testWorkerSkipsNullPayloads() {
        ApplicationProperties properties = new ApplicationProperties();
        MarketplaceProducer producer = mock(MarketplaceProducer.class);
        MarketplacePayloadMapper payloadMapper = mock(MarketplacePayloadMapper.class);
        var worker = new MarketplaceWorker(properties, producer, payloadMapper);

        when(payloadMapper.mapTallySummary(any())).thenReturn(null);

        worker.receive(new TallySummary());

        verify(producer, times(0)).submitUsageRequest(any());
    }

}
