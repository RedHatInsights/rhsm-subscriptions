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
package org.candlepin.subscriptions.task.queue.kafka;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Configuration for a component that produces task messages onto a kafka topic.
 *
 * <p>Should not be imported directly, instead, the component's configuration should import {@link
 * org.candlepin.subscriptions.task.queue.TaskProducerConfiguration} which will handle creation of
 * either in-memory or kafka task queue producers (depending on profile).
 */
@Configuration
@Profile("kafka-queue")
@Import(KafkaConfiguration.class)
public class KafkaTaskProducerConfiguration {
  @Bean
  public ProducerFactory<String, JsonTaskMessage> jsonProducerFactory(
      KafkaProperties kafkaProperties) {
    return new DefaultKafkaProducerFactory<>(getProducerProperties(kafkaProperties));
  }

  @Bean
  public KafkaTemplate<String, JsonTaskMessage> jsonKafkaProducerTemplate(
      ProducerFactory<String, JsonTaskMessage> jsonProducerFactory,
      KafkaConfigurator kafkaConfigurator) {
    return kafkaConfigurator.jsonTaskMessageKafkaTemplate(jsonProducerFactory);
  }

  @NotNull
  public static Map<String, Object> getProducerProperties(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = kafkaProperties.buildProducerProperties();
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return properties;
  }

  @Bean
  public TaskQueue kafkaTaskQueue(KafkaTemplate<String, JsonTaskMessage> producer) {
    return new KafkaTaskQueue(producer);
  }
}
