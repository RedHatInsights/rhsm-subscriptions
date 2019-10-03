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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.insights.pinhead.client.ApiException;
import org.candlepin.insights.pinhead.client.PinheadApiProperties;
import org.candlepin.insights.pinhead.client.model.Consumer;
import org.candlepin.insights.pinhead.client.model.OrgInventory;
import org.candlepin.insights.pinhead.client.model.Pagination;
import org.candlepin.insights.pinhead.client.model.Status;
import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class PinheadServiceTest {
    @Autowired
    private RetryTemplate retryTemplate;

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
        PinheadApi testApi = new PinheadApi() {

            @Override
            public OrgInventory getConsumersForOrg(String orgId, Integer perPage, String offset)
                throws ApiException {
                if (offset == null) {
                    OrgInventory inventory = new OrgInventory();
                    inventory.getFeeds().add(consumer1);
                    inventory.getFeeds().add(consumer2);
                    Status status = new Status();
                    Pagination pagination = new Pagination();
                    pagination.setNextOffset("next");
                    status.setPagination(pagination);
                    inventory.setStatus(status);
                    return inventory;
                }
                else {
                    OrgInventory inventory = new OrgInventory();
                    inventory.getFeeds().add(consumer3);
                    inventory.getFeeds().add(consumer4);
                    Status status = new Status();
                    Pagination pagination = new Pagination();
                    pagination.setNextOffset(null);
                    status.setPagination(pagination);
                    inventory.setStatus(status);
                    return inventory;
                }
            }
        };
        PinheadService service = new PinheadService(new PinheadApiProperties(), testApi, retryTemplate);
        List<Consumer> consumers = new ArrayList<>();
        service.getOrganizationConsumers("123").forEach(consumers::add);
        assertEquals(4, consumers.size());
        assertEquals(consumer1, consumers.get(0));
        assertEquals(consumer2, consumers.get(1));
        assertEquals(consumer3, consumers.get(2));
        assertEquals(consumer4, consumers.get(3));
    }

    @Test
    public void testPinheadServiceRetry() throws Exception {
        PinheadApi testApi = Mockito.mock(PinheadApi.class);
        when(
            testApi.getConsumersForOrg(anyString(), any(Integer.class), nullable(String.class))
        ).thenThrow(ApiException.class);

        // Make the tests run faster!
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        PinheadService service = new PinheadService(new PinheadApiProperties(), testApi, retryTemplate);
        List<Consumer> consumers = new ArrayList<>();
        assertThrows(RuntimeException.class,
            () -> service.getOrganizationConsumers("123").forEach(consumers::add)
        );

        verify(testApi, times(4)).getConsumersForOrg(anyString(), any(Integer.class), nullable(String.class));
    }
}
