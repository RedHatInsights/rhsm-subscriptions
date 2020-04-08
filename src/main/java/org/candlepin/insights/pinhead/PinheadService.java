/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.pinhead;

import org.candlepin.insights.pinhead.client.ApiException;
import org.candlepin.insights.pinhead.client.PinheadApiProperties;
import org.candlepin.insights.pinhead.client.model.OrgInventory;
import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Abstraction around pulling data from pinhead.
 */
@Service
public class PinheadService {

    private static final Logger log = LoggerFactory.getLogger(PinheadService.class);

    private final PinheadApi api;
    private final int batchSize;
    private final RetryTemplate retryTemplate;

    @Autowired
    public PinheadService(PinheadApiProperties apiProperties, PinheadApi api,
        @Qualifier("pinheadRetryTemplate") RetryTemplate retryTemplate) {
        this.batchSize = apiProperties.getRequestBatchSize();
        this.api = api;
        this.retryTemplate = retryTemplate;
    }

    public OrgInventory getPageOfConsumers(String orgId, String offset) throws ApiException {
        return retryTemplate.execute(context -> {
            log.debug("Fetching page of consumers for org {}.", orgId);
            OrgInventory consumersForOrg = api.getConsumersForOrg(orgId, batchSize, offset);
            log.debug("Consumer fetch complete. Found {} for batch of {}.", consumersForOrg.getFeeds().size(),
                batchSize);
            return consumersForOrg;
        });
    }
}
