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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.insights.pinhead.client.ApiException;
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.OrgInventory;
import org.candlepin.insights.pinhead.client.model.Pagination;
import org.candlepin.insights.pinhead.client.model.Status;
import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class PinheadServiceTest {
    private Consumer generateConsumer(String uuid) {
        Consumer consumer = new Consumer();
        consumer.setUuid(uuid);
        return consumer;
    }

    @Test
    public void testPinheadServicePagesConsumers() {
        Consumer consumer1 = generateConsumer("1");
        Consumer consumer2 = generateConsumer("2");
        Consumer consumer3 = generateConsumer("3");
        Consumer consumer4 = generateConsumer("4");
        PinheadService service = new PinheadService(new PinheadApi() {

            @Override
            public OrgInventory getConsumersForOrg(String orgId, Integer perPage, String offset)
                throws ApiException {
                if (offset == null) {
                    OrgInventory inventory = new OrgInventory();
                    inventory.getConsumers().add(consumer1);
                    inventory.getConsumers().add(consumer2);
                    Status status = new Status();
                    Pagination pagination = new Pagination();
                    pagination.setNextOffset("next");
                    status.setPagination(pagination);
                    inventory.setStatus(status);
                    return inventory;
                }
                else {
                    OrgInventory inventory = new OrgInventory();
                    inventory.getConsumers().add(consumer3);
                    inventory.getConsumers().add(consumer4);
                    Status status = new Status();
                    Pagination pagination = new Pagination();
                    pagination.setNextOffset(null);
                    status.setPagination(pagination);
                    inventory.setStatus(status);
                    return inventory;
                }
            }
        });
        List<Consumer> consumers = new ArrayList<>();
        service.getOrganizationConsumers("123").forEach(consumers::add);
        assertEquals(4, consumers.size());
        assertEquals(consumer1, consumers.get(0));
        assertEquals(consumer2, consumers.get(1));
        assertEquals(consumer3, consumers.get(2));
        assertEquals(consumer4, consumers.get(3));
    }
}
