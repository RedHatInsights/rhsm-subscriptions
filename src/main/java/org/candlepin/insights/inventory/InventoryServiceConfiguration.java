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
package org.candlepin.insights.inventory;

import org.candlepin.insights.inventory.client.HostsApiFactory;
import org.candlepin.insights.inventory.client.InventoryServiceProperties;
import org.candlepin.insights.inventory.client.resources.HostsApi;
import org.candlepin.insights.inventory.kafka.HostOperationMessage;
import org.candlepin.insights.inventory.kafka.InventoryServiceKafkaConfigurator;
import org.candlepin.insights.inventory.kafka.KafkaEnabledInventoryService;
import org.candlepin.insights.jackson.ObjectMapperContextResolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Configures all beans required to connect to the inventory service's Kafka instance.
 */
@EnableKafka
@Configuration
@PropertySource("classpath:/rhsm-conduit.properties")
public class InventoryServiceConfiguration {

    @Autowired(required = false)
    private InventoryServiceKafkaConfigurator kafkaConfigurator;

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
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "false", matchIfMissing = true)
    public InventoryService apiInventoryService(HostsApi hostsApi) {
        return new DefaultInventoryService(hostsApi);
    }

    //
    // KAFKA CONFIGURATION
    //

    // The default spring-kafka config property namespace is overridden. This allows
    // separate configuration than that of the kafka instance used by the task queue.
    @Bean
    @ConfigurationProperties(prefix = "rhsm-conduit.inventory-service.kafka")
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "true")
    public KafkaProperties inventoryServiceKafkaProperties() {
        return new KafkaProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "true")
    public InventoryServiceKafkaConfigurator inventoryServiceKafkaConfigurator() {
        return new InventoryServiceKafkaConfigurator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "true")
    public ProducerFactory<String, HostOperationMessage> inventoryServiceKafkaProducerFactory(
        @Qualifier("inventoryServiceKafkaProperties") KafkaProperties kafkaProperties,
        ObjectMapperContextResolver resolver) {
        return kafkaConfigurator.defaultProducerFactory(kafkaProperties, resolver.getContext(null));
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "true")
    public KafkaTemplate<String, HostOperationMessage> inventoryServiceKafkaProducerTemplate(
        ProducerFactory<String, HostOperationMessage> factory) {
        return kafkaConfigurator.taskMessageKafkaTemplate(factory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rhsm-conduit.inventory-service", name = "enableKafka",
        havingValue = "true")
    public InventoryService kafkaInventoryService(
        @Qualifier("inventoryServiceKafkaProducerTemplate")
        KafkaTemplate<String, HostOperationMessage> producer, HostsApi hostsApi,
        InventoryServiceProperties inventoryServiceProperties) {
        return new KafkaEnabledInventoryService(inventoryServiceProperties, producer, hostsApi);
    }
}
