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
package org.candlepin.subscriptions.pinhead;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.pinhead.client.ApiException;
import org.candlepin.subscriptions.pinhead.client.PinheadApiProperties;
import org.candlepin.subscriptions.pinhead.client.model.Consumer;
import org.candlepin.subscriptions.pinhead.client.model.OrgInventory;
import org.candlepin.subscriptions.pinhead.client.resources.PinheadApi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import javax.validation.ConstraintViolationException;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class PinheadServiceTest {
    @Autowired
    private RetryTemplate retryTemplate;

    @Autowired
    private PinheadService pinheadService;

    @Autowired
    private InventoryServiceProperties inventoryServiceProperties;

    @MockBean
    private PinheadApi pinheadApi;

    @TestConfiguration
    static class MockedInventoryServiceConfiguration {
        @Bean
        @Primary
        public InventoryServiceProperties testingInventoryServiceProperties() {
            InventoryServiceProperties inventoryServiceProperties = new InventoryServiceProperties();
            inventoryServiceProperties.setApiKey("changeit");
            return inventoryServiceProperties;
        }
    }

    @Test
    public void testPinheadServiceRetry() throws Exception {
        when(pinheadApi.getConsumersForOrg(
            anyString(), any(Integer.class), nullable(String.class), anyString()
        )).thenThrow(ApiException.class);

        // Make the tests run faster!
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        PinheadService mockBackedService = new PinheadService(inventoryServiceProperties,
            new PinheadApiProperties(), pinheadApi, retryTemplate);

        List<Consumer> consumers = new ArrayList<>();
        assertThrows(ApiException.class, () ->
            consumers.addAll(mockBackedService.getPageOfConsumers("123",
            null, mockBackedService.formattedTime()).getFeeds())
        );

        verify(pinheadApi, times(4)).getConsumersForOrg(anyString(), any(Integer.class),
            nullable(String.class), anyString());
    }

    @Test
    public void testPinheadServiceLastCheckInValidationBad() throws Exception {
        assertThrows(ConstraintViolationException.class,
            () -> pinheadService.getPageOfConsumers("", "", "Does Not Validate")
        );
    }

    @Test
    public void testPinheadServiceLastCheckInValidationBadWithNanos() throws Exception {
        String time = "2020-01-01T13:00:00.725Z";
        assertThrows(ConstraintViolationException.class,
            () -> pinheadService.getPageOfConsumers("org", "offset", time)
        );
    }

    @Test
    public void testPinheadServiceLastCheckInValidationGood() throws Exception {
        String time = "2020-01-01T13:00:00Z";
        OrgInventory expected = new OrgInventory();
        when(pinheadApi.getConsumersForOrg(eq("org"), anyInt(), eq("offset"), eq(time))).thenReturn(expected);

        OrgInventory actual = pinheadService.getPageOfConsumers("org", "offset", time);
        assertSame(expected, actual);
    }
}
