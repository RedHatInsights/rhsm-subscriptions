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
package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.capacity.CapacityReconciliationConfiguration;
import org.candlepin.subscriptions.db.RhsmSubscriptionsDataSourceConfiguration;
import org.candlepin.subscriptions.exception.UnretryableException;
import org.candlepin.subscriptions.resteasy.ResteasyConfiguration;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/** Configuration class for subscription package. */
@Configuration
@Import({
  ResteasyConfiguration.class,
  RhsmSubscriptionsDataSourceConfiguration.class,
  CapacityReconciliationConfiguration.class
})
@EnableJms
@ComponentScan(
    basePackages = {
      "org.candlepin.subscriptions.subscription",
      "org.candlepin.subscriptions.umb",
      "org.candlepin.subscriptions.product"
    },
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class SubscriptionServiceConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "rhsm-subscriptions.subscription")
  public SubscriptionServiceProperties subscriptionServiceProperties() {
    return new SubscriptionServiceProperties();
  }

  @Bean
  @ConditionalOnMissingBean
  KafkaConsumerRegistry kafkaConsumerRegistry() {
    return new KafkaConsumerRegistry();
  }

  @Bean
  public SearchApiFactory searchApiFactory(
      SubscriptionServiceProperties subscriptionServiceProperties) {
    return new SearchApiFactory(subscriptionServiceProperties);
  }

  @Bean
  public RetryTemplate subscriptionServiceRetryTemplate(
      ApplicationProperties applicationProperties) {

    return new RetryTemplateBuilder()
        .maxAttempts(applicationProperties.getSubscription().getMaxRetryAttempts())
        .exponentialBackoff(
            applicationProperties.getSubscription().getBackOffInitialInterval().toMillis(),
            applicationProperties.getSubscription().getBackOffMultiplier(),
            applicationProperties.getSubscription().getBackOffMaxInterval().toMillis())
        .notRetryOn(UnretryableException.class)
        .build();
  }
}
