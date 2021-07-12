package org.candlepin.subscriptions.subscription;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@ComponentScan(basePackages = "org.candlepin.subscriptions.subscription")
public class SubscriptionWorkerConfiguration {

    @Bean
    ConsumerFactory<String, SyncSubscriptionsTask> syncSubscriptionsConsumerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                new StringDeserializer(),
                new JsonDeserializer<>(SyncSubscriptionsTask.class));
    }

    @Bean
    @ConditionalOnMissingBean
    KafkaConsumerRegistry kafkaConsumerRegistry() {
        return new KafkaConsumerRegistry();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, SyncSubscriptionsTask>
    kafkaTallySummaryListenerContainerFactory(
            ConsumerFactory<String, SyncSubscriptionsTask> consumerFactory,
            KafkaProperties kafkaProperties,
            KafkaConsumerRegistry registry) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, SyncSubscriptionsTask>();
        factory.setConsumerFactory(consumerFactory);
        // Concurrency should be set to the number of partitions for the target topic.
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
        if (kafkaProperties.getListener().getIdleEventInterval() != null) {
            factory
                    .getContainerProperties()
                    .setIdleEventInterval(kafkaProperties.getListener().getIdleEventInterval().toMillis());
        }
        // hack to track the Kafka consumers, so SeekableKafkaConsumer can commit when needed
        factory.getContainerProperties().setConsumerRebalanceListener(registry);
        return factory;
    }
}
