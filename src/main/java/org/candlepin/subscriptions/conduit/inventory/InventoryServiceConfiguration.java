/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.conduit.inventory;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.conduit.inventory.kafka.CreateUpdateHostMessage;
import org.candlepin.subscriptions.conduit.inventory.kafka.InventoryServiceKafkaConfigurator;
import org.candlepin.subscriptions.conduit.inventory.kafka.KafkaEnabledInventoryService;
import org.candlepin.subscriptions.inventory.client.HostsApiFactory;
import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Configures all beans required to connect to the inventory service's Kafka instance.
 */
@EnableKafka
@Configuration
public class InventoryServiceConfiguration {

    @Autowired
    private InventoryServiceKafkaConfigurator kafkaConfigurator;

    @Bean
    @Qualifier("hbiObjectMapper")
    ObjectMapper hbiObjectMapper(ApplicationProperties applicationProperties) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, applicationProperties.isPrettyPrintJson());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Tell the mapper to check the classpath for any serialization/deserialization modules
        // such as the Java8 date/time module (JavaTimeModule).
        objectMapper.findAndRegisterModules();
        return objectMapper;
    }

    @Bean
    @ConfigurationProperties(prefix = "rhsm-conduit.inventory-service")
    public InventoryServiceProperties inventoryServiceProperties() {
        return new InventoryServiceProperties();
    }

    @Bean
    public HostsApiFactory hostsApiFactory(InventoryServiceProperties properties) {
        return new HostsApiFactory(properties);
    }

    @Bean
    public ProducerFactory<String, CreateUpdateHostMessage> inventoryServiceKafkaProducerFactory(
        KafkaProperties kafkaProperties,
        @Qualifier("hbiObjectMapper") ObjectMapper mapper) {
        return kafkaConfigurator.defaultProducerFactory(kafkaProperties, mapper);
    }

    @Bean
    public KafkaTemplate<String, CreateUpdateHostMessage> inventoryServiceKafkaProducerTemplate(
        ProducerFactory<String, CreateUpdateHostMessage> factory) {
        return kafkaConfigurator.taskMessageKafkaTemplate(factory);
    }

    @Bean
    public InventoryService kafkaInventoryService(
        @Qualifier("inventoryServiceKafkaProducerTemplate")
        KafkaTemplate<String, CreateUpdateHostMessage> producer,
        InventoryServiceProperties serviceProperties,
        MeterRegistry meterRegistry) {
        return new KafkaEnabledInventoryService(serviceProperties, producer, meterRegistry);
    }
}
