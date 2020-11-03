/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer2;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

import java.util.Map;


@SpringBootTest
@DirtiesContext
@ActiveProfiles("worker")
@TestPropertySource("classpath:/kafka_schema_registry_test.properties")
@EmbeddedKafka(partitions = 1, topics = {"${rhsm-subscriptions.tasks.task-group}"})
public class KafkaTaskQueueSchemaRegistryTest extends KafkaTaskQueueTester {

    @Test
    public void testSendAndReceiveTaskMessage() throws InterruptedException {
        runSendAndReceiveTaskMessageTest();
    }

    public static class TestingKafkaConfigurator extends KafkaConfigurator {

        private MockSchemaRegistryClient registryClient = new MockSchemaRegistryClient();

        @Override
        public DefaultKafkaProducerFactory<String, TaskMessage> defaultProducerFactory(
            KafkaProperties kafkaProperties) {
            DefaultKafkaProducerFactory<String, TaskMessage> factory =
                super.defaultProducerFactory(kafkaProperties);

            // Verify that the configuration specifies the correct serializer and that it is
            // properly configured. Once verified, we can manually instantiate the serializer
            // with the mock schema registry.
            Map<String, Object> factoryConfig = factory.getConfigurationProperties();
            assertEquals(KafkaAvroSerializer.class,
                factoryConfig.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
            assertThat(factoryConfig,
                Matchers.hasKey(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG));

            return new DefaultKafkaProducerFactory(factoryConfig, new StringSerializer(),
                new KafkaAvroSerializer(registryClient, factoryConfig));
        }

        @Override
        public ConsumerFactory<String, TaskMessage> defaultConsumerFactory(KafkaProperties kafkaProperties) {
            ConsumerFactory<String, TaskMessage> factory = super.defaultConsumerFactory(kafkaProperties);

            // Verify that the configuration specifies the correct deserializer and that it is
            // properly configured. Once verified, we can manually instantiate the deserializer
            // with the mock schema registry.
            Map<String, Object> factoryConfig = factory.getConfigurationProperties();
            assertEquals(ErrorHandlingDeserializer2.class,
                factoryConfig.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
            assertEquals(KafkaAvroDeserializer.class,
                factoryConfig.get(ErrorHandlingDeserializer2.VALUE_DESERIALIZER_CLASS));
            assertThat(factoryConfig,
                Matchers.hasKey(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG));

            KafkaAvroDeserializer delegate = new KafkaAvroDeserializer(registryClient, factoryConfig);
            ErrorHandlingDeserializer2 errorDeserializer = new ErrorHandlingDeserializer2(delegate);
            return new DefaultKafkaConsumerFactory<>(factoryConfig, new StringDeserializer(),
                errorDeserializer);
        }
    }

    @TestConfiguration
    static class KafkaTaskQueueSchemaRegistryTestConfiguration {

        @Bean
        @Primary
        public KafkaConfigurator testingConfigurator() {
            return new TestingKafkaConfigurator();
        }

    }

}
