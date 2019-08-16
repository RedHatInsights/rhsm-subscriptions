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
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.OrgInventory;
import org.candlepin.insights.pinhead.client.model.Status;
import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Abstraction around pulling data from pinhead.
 */
@Service
public class PinheadService {
    public static final int BATCH_SIZE = 100; // TODO profile to determine a better batch size

    private final PinheadApi api;
    private final RetryTemplate retryTemplate;

    private class PagedConsumerIterator implements Iterator<Consumer> {

        private final String orgId;

        private List<Consumer> consumers;
        private String nextOffset;

        PagedConsumerIterator(String orgId) {
            this.orgId = orgId;
            fetchPage();
        }

        private void fetchPage() {
            try {
                retryTemplate.execute((RetryCallback<Void, ApiException>) context -> {
                    OrgInventory consumersForOrg = api.getConsumersForOrg(orgId, BATCH_SIZE, nextOffset);
                    consumers = consumersForOrg.getFeeds();
                    Status status = consumersForOrg.getStatus();
                    if (status != null && status.getPagination() != null) {
                        nextOffset = consumersForOrg.getStatus().getPagination().getNextOffset();
                    }
                    else {
                        nextOffset = null;
                    }
                    return null;
                });
            }
            catch (ApiException e) {
                throw new RuntimeException("Error while fetching paged consumers", e);
            }
        }

        @Override
        public boolean hasNext() {
            return (consumers != null && !consumers.isEmpty()) || nextOffset != null && !nextOffset.isEmpty();
        }

        @Override
        public Consumer next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Current page is the last page");
            }
            if (consumers.isEmpty()) {
                fetchPage();
            }
            return consumers.remove(0);
        }
    }

    @Autowired
    public PinheadService(PinheadApi api, @Qualifier("pinheadRetryTemplate") RetryTemplate retryTemplate) {
        this.api = api;
        this.retryTemplate = retryTemplate;
    }

    public Iterable<Consumer> getOrganizationConsumers(String orgId) {
        return () -> new PagedConsumerIterator(orgId);
    }
}
