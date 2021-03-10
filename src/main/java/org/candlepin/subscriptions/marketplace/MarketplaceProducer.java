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

import org.candlepin.subscriptions.marketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import io.micrometer.core.annotation.Timed;

/**
 * Component that is responsible for emitting usage info to Marketplace, including handling retries.
 */
@Service
public class MarketplaceProducer {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceProducer.class);

    private final MarketplaceService marketplaceService;
    private final RetryTemplate retryTemplate;

    @Autowired
    MarketplaceProducer(MarketplaceService marketplaceService,
        @Qualifier("marketplaceRetryTemplate") RetryTemplate retryTemplate) {
        this.marketplaceService = marketplaceService;
        this.retryTemplate = retryTemplate;
    }

    @Timed("rhsm-subscriptions.marketplace.usage")
    public StatusResponse submitUsageRequest(UsageRequest usageRequest) throws ApiException {
        // NOTE: https://issues.redhat.com/browse/ENT-3609 will address failures
        return retryTemplate.execute(context -> tryRequest(usageRequest));
    }

    @Timed("rhsm-subscriptions.marketplace.usage.request")
    private StatusResponse tryRequest(UsageRequest usageRequest) throws ApiException {
        StatusResponse status = marketplaceService.submitUsageEvents(usageRequest);
        log.debug("Marketplace response: {}", status);
        return status;
    }
}
