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
package org.candlepin.subscriptions.user;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/**
 * Spring configuration for the IT User service client.
 *
 * <p>Note that the service is called "User", but the API we are using is an "Account API". The IT
 * service has other APIs around account & user management, but we're only interested in the
 * findAccount API.
 */
@Configuration
@ComponentScan(
    basePackages = "org.candlepin.subscriptions.user",
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class UserServiceClientConfiguration {
  @Bean
  @ConfigurationProperties(prefix = "rhsm-subscriptions.user-service")
  public UserServiceProperties userServiceProperties() {
    return new UserServiceProperties();
  }

  @Bean
  public RetryPolicy defaultUserServicePolicy(UserServiceProperties properties) {
    return new MaxAttemptsRetryPolicy(properties.getMaxAttempts());
  }

  @Bean
  public Classifier<Throwable, RetryPolicy> notFoundClassifier(
      RetryPolicy defaultUserServicePolicy) {
    return new NotFoundSubscriptionsExceptionClassifier(defaultUserServicePolicy);
  }

  @Bean
  public RetryPolicy composedRetryPolicy(Classifier<Throwable, RetryPolicy> notFoundClassifier) {
    /* The ExceptionClassifierRetryPolicy is built around a Classifier object that takes
     * Throwables and returns RetryPolicies based on the Throwable.  I think the naming is a
     * little confusing, but the important thing to remember is that the critical logic is
     * located in the Classifer.
     */
    ExceptionClassifierRetryPolicy exceptionClassifierRetryPolicy =
        new ExceptionClassifierRetryPolicy();
    exceptionClassifierRetryPolicy.setExceptionClassifier(notFoundClassifier);
    return exceptionClassifierRetryPolicy;
  }

  @Bean
  @Qualifier("userServiceRetry")
  public RetryTemplate userServiceRetry(
      UserServiceProperties properties, RetryPolicy composedRetryPolicy) {
    return new RetryTemplateBuilder()
        .exponentialBackoff(
            properties.getBackOffInitialInterval().toMillis(),
            properties.getBackOffMultiplier(),
            properties.getBackOffMaxInterval().toMillis())
        .customPolicy(composedRetryPolicy)
        .build();
  }

  @Bean
  public AccountApiFactory accountApiFactory(UserServiceProperties properties) {
    return new AccountApiFactory(properties);
  }
}
