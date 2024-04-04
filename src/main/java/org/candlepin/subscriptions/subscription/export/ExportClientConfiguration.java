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
package org.candlepin.subscriptions.subscription.export;

import com.redhat.swatch.clients.export.api.client.ExportApiClientFactory;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Configuration that should be imported for any component that needs to be able to use Subscription
 * export logic.
 */
@Configuration
@ComponentScan(
    basePackages = {"org.candlepin.subscriptions.subscription"},
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class ExportClientConfiguration {

  public static final String EXPORT_API_QUALIFIER = "exportApiProperties";

  /**
   * Load values from the application properties file prefixed with
   * "rhsm-subscriptions.export-service". For example,
   * "rhsm-subscriptions.export-service.keystore-password=password" will be injected into the
   * keystorePassword field. The hyphen is not necessary but it improves readability. Rather than
   * use the ConfigurationProperties annotation on the class itself and the
   * EnableConfigurationProperties annotation on ApplicationConfiguration, we construct and bind
   * values to the class here so that our sub-projects will not need to have Spring Boot on the
   * class path (since it's Spring Boot that provides those annotations).
   *
   * @return an X509ApiClientFactoryConfiguration populated with values from the various property
   *     sources.
   */
  @Bean(name = EXPORT_API_QUALIFIER)
  @ConfigurationProperties(prefix = "rhsm-subscriptions.export-service")
  public HttpClientProperties exportApiProperties() {
    return new HttpClientProperties();
  }

  /**
   * Build the BeanFactory implementation ourselves since the docs say "Implementations are not
   * supposed to rely on annotation-driven injection or other reflective facilities."
   *
   * @param properties containing the configuration needed by the factory
   * @return a configured ExportApiFactory
   */
  @Bean
  public ExportApiClientFactory exportClientApiFactory(
      @Qualifier(EXPORT_API_QUALIFIER) HttpClientProperties properties) {
    return new ExportApiClientFactory(properties);
  }
}
