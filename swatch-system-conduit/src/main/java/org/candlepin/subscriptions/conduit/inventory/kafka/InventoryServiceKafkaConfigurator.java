/*
 * Copyright Red Hat, Inc.
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
package org.candlepin.subscriptions.conduit.inventory.kafka;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encapsulates the creation of all components required for producing Kafka messages for the
 * inventory service.
 */
@Component
public class InventoryServiceKafkaConfigurator {

  public DefaultKafkaProducerFactory<String, CreateUpdateHostMessage> defaultProducerFactory(
      KafkaProperties kafkaProperties, JsonMapper jsonMapper) {
    Map<String, Object> producerConfig = kafkaProperties.buildProducerProperties();
    producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    DefaultKafkaProducerFactory<String, CreateUpdateHostMessage> factory =
        new DefaultKafkaProducerFactory<>(producerConfig);
    // Because inventory requires us to not send JSON fields that have null values,
    // we need to customize the JsonMapper used by spring-kafka. There is no way to customize
    // it via configuration properties, so we use the custom one that is configured for the
    // application that is created via the ApplicationConfiguration.
    // Using JacksonJsonSerializer for Jackson 3 compatibility.
    factory.setValueSerializer(new JacksonJsonSerializer<CreateUpdateHostMessage>(jsonMapper));
    return factory;
  }

  public KafkaTemplate<String, CreateUpdateHostMessage> taskMessageKafkaTemplate(
      ProducerFactory<String, CreateUpdateHostMessage> factory, KafkaProperties kafkaProperties) {
    KafkaTemplate<String, CreateUpdateHostMessage> kafkaTemplate = new KafkaTemplate<>(factory);
    kafkaTemplate.setObservationEnabled(kafkaProperties.getTemplate().isObservationEnabled());
    return kafkaTemplate;
  }
}
