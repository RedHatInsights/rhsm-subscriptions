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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.candlepin.subscriptions.json.UsageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class TallyWorkerConfigurationTest {

  @Autowired private TallyWorkerConfiguration configuration;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private KafkaProperties kafkaProperties;

  @Test
  void testUsageConsumerFactoryWrapsDeserializerWithErrorHandling() throws Exception {
    ConsumerFactory<String, UsageEvent> consumerFactory =
        configuration.usageConsumerFactory(
            kafkaProperties, objectMapper, configuration.usageTopicProperties());

    assertNotNull(consumerFactory, "ConsumerFactory should not be null");

    var consumer = consumerFactory.createConsumer();
    assertNotNull(consumer, "Consumer should be created successfully");
    consumer.close();
  }

  @Test
  void testUsageDeadLetterTopicPropertiesIsConfigured() {
    var deadLetterProperties = configuration.usageDeadLetterTopicProperties();

    assertNotNull(deadLetterProperties, "Dead letter topic properties should not be null");
  }

  @Test
  void testUsageDeadLetterProducerFactory() {
    var producerFactory =
        configuration.usageDeadLetterProducerFactory(kafkaProperties, objectMapper);

    assertNotNull(producerFactory, "Dead letter producer factory should not be null");
    var producer = producerFactory.createProducer();
    assertNotNull(producer, "Producer should be created successfully");
    producer.close();
  }

  @Test
  void testUsageDeadLetterKafkaTemplate() {
    var producerFactory =
        configuration.usageDeadLetterProducerFactory(kafkaProperties, objectMapper);
    var kafkaTemplate =
        configuration.usageDeadLetterKafkaTemplate(producerFactory, kafkaProperties);

    assertNotNull(kafkaTemplate, "Kafka template should not be null");
  }

  @Test
  void testUsageDeadLetterKafkaErrorHandler() {
    var producerFactory =
        configuration.usageDeadLetterProducerFactory(kafkaProperties, objectMapper);
    var kafkaTemplate =
        configuration.usageDeadLetterKafkaTemplate(producerFactory, kafkaProperties);
    var errorHandler =
        configuration.usageDeadLetterKafkaErrorHandler(
            kafkaTemplate, configuration.usageDeadLetterTopicProperties());

    assertNotNull(errorHandler, "Error handler should not be null");
  }

  @Test
  void testKafkaUsageListenerContainerFactoryHasErrorHandler() {
    var consumerFactory =
        configuration.usageConsumerFactory(
            kafkaProperties, objectMapper, configuration.usageTopicProperties());
    var producerFactory =
        configuration.usageDeadLetterProducerFactory(kafkaProperties, objectMapper);
    var kafkaTemplate =
        configuration.usageDeadLetterKafkaTemplate(producerFactory, kafkaProperties);
    var errorHandler =
        configuration.usageDeadLetterKafkaErrorHandler(
            kafkaTemplate, configuration.usageDeadLetterTopicProperties());

    var containerFactory =
        configuration.kafkaUsageListenerContainerFactory(
            consumerFactory, kafkaProperties, null, errorHandler);

    assertNotNull(containerFactory, "Container factory should not be null");
    assertNotNull(
        containerFactory.getContainerProperties(),
        "Container factory should have container properties configured");
  }

  @Test
  void testErrorHandlingDeserializerHandlesMalformedJson() {
    var jsonDeserializer = new JsonDeserializer<>(UsageEvent.class, objectMapper, false);
    var errorHandlingDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

    var malformedJson = "{invalid json}".getBytes(StandardCharsets.UTF_8);

    assertDoesNotThrow(
        () -> errorHandlingDeserializer.deserialize("test-topic", malformedJson),
        "ErrorHandlingDeserializer should handle malformed JSON without throwing");
  }
}
