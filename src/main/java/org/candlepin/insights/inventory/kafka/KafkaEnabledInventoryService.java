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

import org.candlepin.insights.inventory.ConduitFacts;
import org.candlepin.insights.inventory.InventoryService;
import org.candlepin.insights.inventory.client.InventoryServiceProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An InventoryService implementation that includes a Kafka producer that is capable
 * of sending messages to the inventory service's Kafka instance. A message is sent
 * as soon as a host update is scheduled.
 */
public class KafkaEnabledInventoryService extends InventoryService {

    private static final Logger log = LoggerFactory.getLogger(KafkaEnabledInventoryService.class);

    private final KafkaTemplate<String, HostOperationMessage> producer;
    private final String hostIngressTopic;

    public KafkaEnabledInventoryService(InventoryServiceProperties serviceProperties,
        KafkaTemplate<String, HostOperationMessage> producer) {
        // Flush updates as soon as they get scheduled.
        super(serviceProperties, 1);
        this.producer = producer;
        this.hostIngressTopic = serviceProperties.getKafkaHostIngressTopic();
    }

    @Override
    protected void sendHostUpdate(List<ConduitFacts> facts) {
        if (facts.isEmpty()) {
            log.info("No facts to report!");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (ConduitFacts factSet : facts) {
            // Attempt to send the host create/update message. If the send fails for any reason,
            // log the error and move on to the next one.
            try {
                log.debug("Sending host inventory message: {}:{}:{}", factSet.getAccountNumber(),
                    factSet.getOrgId(), factSet.getSubscriptionManagerId());
                producer.send(hostIngressTopic, new CreateUpdateHostMessage(createHost(factSet, now)));
            }
            catch (Exception e) {
                log.error("Unable to send host create/update message.", e);
            }
        }
    }

}
