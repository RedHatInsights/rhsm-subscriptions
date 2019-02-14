/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
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
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Abstraction around pulling data from pinhead.
 */
@Component
public class PinheadService {
    public static final int BATCH_SIZE = 100; // TODO profile to determine a better batch size

    private final PinheadApi api;

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
                OrgInventory consumersForOrg = api.getConsumersForOrg(orgId, BATCH_SIZE, nextOffset);
                consumers = consumersForOrg.getConsumers();

                Status status = consumersForOrg.getStatus();
                if (status != null && status.getPagination() != null) {
                    nextOffset = consumersForOrg.getStatus().getPagination().getNextOffset();
                }
                else {
                    nextOffset = null;
                }
            }
            catch (ApiException e) {
                throw new RuntimeException("Error while fetching paged consumers", e);
            }
        }

        @Override
        public boolean hasNext() {
            return !consumers.isEmpty() || nextOffset != null && !nextOffset.equals("");
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
    public PinheadService(PinheadApi api) {
        this.api = api;
    }

    public Iterable<Consumer> getOrganizationConsumers(String orgId) {
        return () -> new PagedConsumerIterator(orgId);
    }
}
