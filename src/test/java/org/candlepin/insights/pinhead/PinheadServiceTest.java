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
    public void testPinheadServiceRetry() throws Exception {
        PinheadApi testApi = Mockito.mock(PinheadApi.class);
        when(
            testApi.getConsumersForOrg(anyString(), any(Integer.class), nullable(String.class))
        ).thenThrow(ApiException.class);

        // Make the tests run faster!
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
        PinheadService service = new PinheadService(new PinheadApiProperties(), testApi, retryTemplate);
        List<Consumer> consumers = new ArrayList<>();
        assertThrows(ApiException.class,
            () -> consumers.addAll(service.getPageOfConsumers("123", null).getFeeds())
        );

        verify(testApi, times(4)).getConsumersForOrg(anyString(), any(Integer.class), nullable(String.class));
    }
}
