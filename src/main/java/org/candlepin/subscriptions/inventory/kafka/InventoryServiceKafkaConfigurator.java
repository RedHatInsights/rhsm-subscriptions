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
package org.candlepin.subscriptions.inventory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Encapsulates the creation of all components required for producing Kafka
 * messages for the inventory service.
 */
public class InventoryServiceKafkaConfigurator {

    public DefaultKafkaProducerFactory<String, HostOperationMessage> defaultProducerFactory(
        KafkaProperties kafkaProperties, ObjectMapper mapper) {
        Map<String, Object> producerConfig = kafkaProperties.buildProducerProperties();
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DefaultKafkaProducerFactory<String, HostOperationMessage> factory =
            new DefaultKafkaProducerFactory<>(producerConfig);
        // Because inventory requires us to not sent JSON fields that have null values,
        // we need to customize the ObjectMapper used by spring-kafka. There is no way to customize
        // it via configuration properties, so we use the custom one that is configured for the
        // application that is created via the ApplicationConfiguration.
        factory.setValueSerializer(new JsonSerializer<>(mapper));
        return factory;
    }

    public KafkaTemplate<String, HostOperationMessage> taskMessageKafkaTemplate(
        ProducerFactory<String, HostOperationMessage> factory) {
        return new KafkaTemplate<>(factory);
    }
}
