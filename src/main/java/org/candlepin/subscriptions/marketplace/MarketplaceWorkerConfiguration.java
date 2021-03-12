/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.marketplace;

import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.subscription.SubscriptionConfiguration;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/**
 * Configuration for the Marketplace integration worker.
 */
@Profile("marketplace")
@ComponentScan(basePackages = "org.candlepin.subscriptions.marketplace")
@Import(SubscriptionConfiguration.class)
public class MarketplaceWorkerConfiguration {
    @Bean
    @Qualifier("marketplaceRetryTemplate")
    public RetryTemplate marketplaceRetryTemplate(MarketplaceProperties properties) {
        return new RetryTemplateBuilder()
            .maxAttempts(properties.getMaxAttempts())
            .exponentialBackoff(properties.getBackOffInitialInterval().toMillis(),
                properties.getBackOffMultiplier(),
                properties.getBackOffMaxInterval().toMillis())
            .build();
    }

    @Bean
    ConsumerFactory<String, TallySummary> tallySummaryConsumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(),
            new StringDeserializer(), new JsonDeserializer<>(TallySummary.class));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, TallySummary> kafkaTallySummaryListenerContainerFactory(
        ConsumerFactory<String, TallySummary> consumerFactory) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, TallySummary>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    /**
     * Build the BeanFactory implementation ourselves since the docs say "Implementations are not supposed
     * to rely on annotation-driven injection or other reflective facilities."
     * @param properties containing the MarketplaceProperties needed by the factory
     * @return a configured MarketplaceApiFactory
     */
    @Bean
    public MarketplaceApiFactory marketplaceApiFactory(MarketplaceProperties properties) {
        return new MarketplaceApiFactory(properties);
    }
}
