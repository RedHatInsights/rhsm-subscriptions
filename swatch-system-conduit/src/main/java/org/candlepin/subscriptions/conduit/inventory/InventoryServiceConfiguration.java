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
package org.candlepin.subscriptions.conduit.inventory;

import io.micrometer.core.instrument.MeterRegistry;
import org.candlepin.subscriptions.conduit.inventory.kafka.CreateUpdateHostMessage;
import org.candlepin.subscriptions.conduit.inventory.kafka.InventoryServiceKafkaConfigurator;
import org.candlepin.subscriptions.conduit.inventory.kafka.KafkaEnabledInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

/** Configures all beans required to connect to the inventory service's Kafka instance. */
@EnableKafka
@Configuration
public class InventoryServiceConfiguration {

  @Autowired private InventoryServiceKafkaConfigurator kafkaConfigurator;

  @Bean
  @Qualifier("hbiObjectMapper")
  JsonMapper hbiObjectMapper(InventoryServiceProperties inventoryServiceProperties) {
    // Jackson 3: Use builder pattern with changeDefaultPropertyInclusion
    // Note: Jackson 3 includes JavaTime and JDK8 modules by default
    return JsonMapper.builder()
        .defaultDateFormat(new StdDateFormat().withColonInTimeZone(true))
        .configure(
            SerializationFeature.INDENT_OUTPUT, inventoryServiceProperties.isPrettyPrintJson())
        .changeDefaultPropertyInclusion(
            include ->
                include.withValueInclusion(
                    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }

  @Bean
  @ConfigurationProperties(prefix = "rhsm-conduit.inventory-service")
  public InventoryServiceProperties inventoryServiceProperties() {
    return new InventoryServiceProperties();
  }

  @Bean
  public ProducerFactory<String, CreateUpdateHostMessage> inventoryServiceKafkaProducerFactory(
      KafkaProperties kafkaProperties, @Qualifier("hbiObjectMapper") JsonMapper jsonMapper) {
    return kafkaConfigurator.defaultProducerFactory(kafkaProperties, jsonMapper);
  }

  @Bean
  public KafkaTemplate<String, CreateUpdateHostMessage> inventoryServiceKafkaProducerTemplate(
      ProducerFactory<String, CreateUpdateHostMessage> factory, KafkaProperties kafkaProperties) {
    return kafkaConfigurator.taskMessageKafkaTemplate(factory, kafkaProperties);
  }

  @Bean
  public InventoryService kafkaInventoryService(
      @Qualifier("inventoryServiceKafkaProducerTemplate")
          KafkaTemplate<String, CreateUpdateHostMessage> producer,
      InventoryServiceProperties serviceProperties,
      MeterRegistry meterRegistry,
      RetryTemplate kafkaRetryTemplate) {
    return new KafkaEnabledInventoryService(
        serviceProperties, producer, meterRegistry, kafkaRetryTemplate);
  }

  @Bean(name = "kafkaRetryTemplate")
  public RetryTemplate kafkaRetryTemplate() {
    return RetryTemplate.builder()
        .retryOn(KafkaException.class)
        .maxAttempts(4)
        .uniformRandomBackoff(100, 500)
        .build();
  }
}
