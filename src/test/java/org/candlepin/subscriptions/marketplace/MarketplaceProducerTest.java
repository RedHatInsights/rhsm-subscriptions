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
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;

import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

class MarketplaceProducerTest {

    @Test
    void testMarketplaceProducerRetry() throws Exception {
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
            .maxAttempts(2)
            .noBackoff()
            .build();
        MarketplaceService marketplaceService = mock(MarketplaceService.class);
        MarketplaceProducer marketplaceProducer = new MarketplaceProducer(marketplaceService, retryTemplate);

        when(marketplaceService.submitUsageEvents(any())).thenThrow(SubscriptionsException.class);

        assertThrows(SubscriptionsException.class, () ->
            marketplaceProducer.submitUsageRequest(new UsageRequest())
        );

        verify(marketplaceService, times(2)).submitUsageEvents(any());
    }
}
