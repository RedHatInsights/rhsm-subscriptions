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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.marketplace.api.model.BatchStatus;
import org.candlepin.subscriptions.marketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;

import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MarketplaceProducerTest {

    @Test
    void testMarketplaceProducerRetry() throws Exception {
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
            .maxAttempts(2)
            .noBackoff()
            .build();
        MarketplaceService marketplaceService = mock(MarketplaceService.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceProducer marketplaceProducer = new MarketplaceProducer(marketplaceService, retryTemplate,
            registry, new MarketplaceProperties());
        var rejectedCounter = registry.counter("rhsm-subscriptions.marketplace.batch.rejected");

        when(marketplaceService.submitUsageEvents(any())).thenThrow(SubscriptionsException.class);

        var usageRequest = new UsageRequest();
        assertThrows(SubscriptionsException.class, () ->
            marketplaceProducer.submitUsageRequest(usageRequest)
        );

        verify(marketplaceService, times(2)).submitUsageEvents(any());
        assertEquals(1.0, rejectedCounter.count());
    }

    @Test
    void testMarketplaceProducerRecordsSuccessfulBatch() throws ApiException {
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
            .maxAttempts(2)
            .noBackoff()
            .build();
        MarketplaceService marketplaceService = mock(MarketplaceService.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceProducer marketplaceProducer = new MarketplaceProducer(marketplaceService, retryTemplate,
            registry, new MarketplaceProperties());
        var acceptedCounter = registry.counter("rhsm-subscriptions.marketplace.batch.accepted");

        when(marketplaceService.submitUsageEvents(any()))
            .thenReturn(new StatusResponse().status("inprogress").addDataItem(new BatchStatus().batchId(
            "foo")));
        when(marketplaceService.getUsageBatchStatus("foo")).thenReturn(new StatusResponse().status(
            "accepted"));

        var usageRequest = new UsageRequest();
        marketplaceProducer.submitUsageRequest(usageRequest);

        assertEquals(1.0, acceptedCounter.count());
    }

    @Test
    void testMarketplaceProducerRecordsUnverifiedBatch() throws ApiException {
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
            .maxAttempts(2)
            .noBackoff()
            .build();
        MarketplaceService marketplaceService = mock(MarketplaceService.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceProducer marketplaceProducer = new MarketplaceProducer(marketplaceService, retryTemplate,
            registry, new MarketplaceProperties());
        var unverifiedCounter = registry.counter("rhsm-subscriptions.marketplace.batch.unverified");

        when(marketplaceService.submitUsageEvents(any()))
            .thenReturn(new StatusResponse().status("inprogress").addDataItem(new BatchStatus().batchId(
            "foo")));
        when(marketplaceService.getUsageBatchStatus("foo")).thenReturn(new StatusResponse().status(
            "inprogress"));

        var usageRequest = new UsageRequest();
        marketplaceProducer.submitUsageRequest(usageRequest);

        verify(marketplaceService, times(2)).getUsageBatchStatus("foo");
        assertEquals(1.0, unverifiedCounter.count());
    }

    @Test
    void testMarketplaceProducerRecordsRejectedBatch() throws ApiException {
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
            .maxAttempts(2)
            .noBackoff()
            .build();
        MarketplaceService marketplaceService = mock(MarketplaceService.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceProducer marketplaceProducer = new MarketplaceProducer(marketplaceService, retryTemplate,
            registry, new MarketplaceProperties());
        var rejectedCounter = registry.counter("rhsm-subscriptions.marketplace.batch.rejected");

        when(marketplaceService.submitUsageEvents(any()))
            .thenReturn(new StatusResponse().status("inprogress").addDataItem(new BatchStatus().batchId(
            "foo")));
        when(marketplaceService.getUsageBatchStatus("foo")).thenReturn(new StatusResponse().status(
            "failed"));

        var usageRequest = new UsageRequest();
        marketplaceProducer.submitUsageRequest(usageRequest);

        assertEquals(1.0, rejectedCounter.count());
    }
}
