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
package org.candlepin.subscriptions.conduit.rhsm;

import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiFactory;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/** Configures the RHSM client. */
@Configuration
public class RhsmClientConfiguration {

  /**
   * Load values from the application properties file prefixed with "rhsm-conduit.rhsm". For
   * example, "rhsm-conduit.rhsm.keystore-password=password" will be injected into the
   * keystorePassword field. The hyphen is not necessary but it improves readability. Rather than
   * use the ConfigurationProperties annotation on the class itself and the
   * EnableConfigurationProperties annotation on ApplicationConfiguration, we construct and bind
   * values to the class here so that our sub-projects will not need to have Spring Boot on the
   * class path (since it's Spring Boot that provides those annotations).
   *
   * @return an X509ApiClientFactoryConfiguration populated with values from the various property
   *     sources.
   */
  @Bean
  @ConfigurationProperties(prefix = "rhsm-conduit.rhsm")
  public RhsmApiProperties rhsmApiProperties() {
    return new RhsmApiProperties();
  }

  /**
   * Build the BeanFactory implementation ourselves since the docs say "Implementations are not
   * supposed to rely on annotation-driven injection or other reflective facilities."
   *
   * @param properties containing the configuration needed by the factory
   * @return a configured RhsmApiFactory
   */
  @Bean
  public RhsmApiFactory rhsmApiFactory(RhsmApiProperties properties) {
    return new RhsmApiFactory(properties);
  }

  @Bean(name = "rhsmRetryTemplate")
  public RetryTemplate retryTemplate() {
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(4);

    ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
    backOffPolicy.setInitialInterval(1000);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);
    return retryTemplate;
  }
}
