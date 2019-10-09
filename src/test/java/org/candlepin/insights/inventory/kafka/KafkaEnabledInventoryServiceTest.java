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
package org.candlepin.insights.inventory.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.client.InventoryServiceProperties;
import org.candlepin.insights.inventory.client.model.FactSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Arrays;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class KafkaEnabledInventoryServiceTest {

    @Mock
    private KafkaTemplate producer;

    @Test
    public void ensureKafkaProducerSendsHostMessage() {

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CreateUpdateHostMessage> messageCaptor =
            ArgumentCaptor.forClass(CreateUpdateHostMessage.class);

        when(producer.send(topicCaptor.capture(), messageCaptor.capture())).thenReturn(null);

        ConduitFacts expectedFacts = new ConduitFacts();
        expectedFacts.setFqdn("my_fqdn");
        expectedFacts.setAccountNumber("my_account");
        expectedFacts.setOrgId("my_org");
        expectedFacts.setCpuCores(25);
        expectedFacts.setCpuSockets(45);

        InventoryServiceProperties props = new InventoryServiceProperties();
        KafkaEnabledInventoryService service = new KafkaEnabledInventoryService(props, producer);
        service.sendHostUpdate(Arrays.asList(expectedFacts));

        assertEquals(props.getKafkaHostIngressTopic(), topicCaptor.getValue());

        CreateUpdateHostMessage message = messageCaptor.getValue();
        assertNotNull(message);
        assertEquals("add_host", message.getOperation());
        assertEquals(expectedFacts.getAccountNumber(), message.getData().getAccount());
        assertEquals(expectedFacts.getFqdn(), message.getData().getFqdn());

        assertNotNull(message.getData().getFacts());
        assertEquals(1, message.getData().getFacts().size());
        FactSet rhsm = message.getData().getFacts().get(0);
        assertEquals("rhsm", rhsm.getNamespace());

        Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
        assertEquals(expectedFacts.getOrgId(), (String) rhsmFacts.get("orgId"));
        assertEquals(expectedFacts.getCpuCores(), (Integer) rhsmFacts.get("CPU_CORES"));
        assertEquals(expectedFacts.getCpuSockets(), (Integer) rhsmFacts.get("CPU_SOCKETS"));
    }

    @Test
    public void ensureNoMessageWithEmptyFactList() {
        InventoryServiceProperties props = new InventoryServiceProperties();
        KafkaEnabledInventoryService service = new KafkaEnabledInventoryService(props, producer);
        service.sendHostUpdate(Arrays.asList());

        verifyZeroInteractions(producer);
    }

    @Test
    public void ensureMessageSentWhenHostUpdateScheduled() {
        InventoryServiceProperties props = new InventoryServiceProperties();
        KafkaEnabledInventoryService service = new KafkaEnabledInventoryService(props, producer);
        service.scheduleHostUpdate(new ConduitFacts());
        service.scheduleHostUpdate(new ConduitFacts());

        verify(producer, times(2)).send(anyString(), any());
    }
}
