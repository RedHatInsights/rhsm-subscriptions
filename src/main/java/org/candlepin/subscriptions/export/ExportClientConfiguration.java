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
package org.candlepin.subscriptions.export;

import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.ClientConfigBuilder;
import com.redhat.swatch.clients.ClientConnection;
import com.redhat.swatch.clients.TokenProvider;
import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.export.api.ExportDelegate;
import com.redhat.swatch.export.api.ExportDelegateImpl;
import java.io.IOException;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.Resource;

/**
 * Configuration that should be imported for any component that needs to be able to use Subscription
 * export logic.
 */
@Configuration
@ComponentScan(
    basePackages = {"org.candlepin.subscriptions.export"},
    // Prevent TestConfiguration annotated classes from being picked up by ComponentScan
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.CUSTOM,
          classes = AutoConfigurationExcludeFilter.class)
    })
public class ExportClientConfiguration {

  public static final String EXPORT_API_CLIENT = "exportServiceClientConfig";
  public static final String EXPORT_API_QUALIFIER = "exportApiProperties";
  public static final String EXPORT_API_FALLBACK_QUALIFIER = "exportApiFallbackProperties";

  /**
   * Primary (Clowder V2) connection plus shared {@code psk} / {@code endpoint-mode}, bound from
   * {@code rhsm-subscriptions.export-service.*}.
   *
   * @return export-service properties bound from the property sources
   */
  @Bean(name = EXPORT_API_QUALIFIER)
  @ConfigurationProperties(prefix = "rhsm-subscriptions.export-service")
  public ExportConnectionProperties exportServiceConnectionProperties() {
    return new ExportConnectionProperties();
  }

  /**
   * Clowder fallback connection settings under {@code
   * rhsm-subscriptions.export-service.fallback.*}.
   *
   * @return fallback HTTP client properties bound from the property sources
   */
  @Bean(name = EXPORT_API_FALLBACK_QUALIFIER)
  @ConfigurationProperties(prefix = "rhsm-subscriptions.export-service.fallback")
  public HttpClientProperties exportServiceFallbackConnectionProperties() {
    return new HttpClientProperties();
  }

  @Bean(name = EXPORT_API_CLIENT)
  ClientConfig exportServiceConfig(
      @Qualifier(EXPORT_API_QUALIFIER) ExportConnectionProperties primaryProperties,
      @Qualifier(EXPORT_API_FALLBACK_QUALIFIER) HttpClientProperties fallbackProperties) {
    ClientConnection fallback =
        new ClientConnection(
            "v1",
            fallbackProperties.getUrl(),
            resourcePath(fallbackProperties.getTruststore()),
            fallbackProperties.getTruststorePassword(),
            fallbackProperties.getTruststoreType(),
            null);
    ClientConnection primary =
        new ClientConnection(
            "v2",
            primaryProperties.getUri(),
            resourcePath(primaryProperties.getTruststore()),
            primaryProperties.getTruststorePassword(),
            primaryProperties.getTruststoreType(),
            primaryProperties.getAuthenticated());
    return ClientConfigBuilder.with("export-service", primaryProperties.getPsk(), primary, fallback)
        .readTimeout(primaryProperties.getReadTimeout())
        .build(primaryProperties.getEndpointMode());
  }

  /**
   * Build the BeanFactory implementation ourselves since the docs say "Implementations are not
   * supposed to rely on annotation-driven injection or other reflective facilities."
   *
   * @param clientConfig client config
   * @param tokenProvider OAuth2 token provider
   * @return a configured ExportApi FactoryBean
   */
  @Bean
  public ExportApiClientFactory exportClientApiFactory(
      @Qualifier(EXPORT_API_CLIENT) ClientConfig clientConfig, TokenProvider tokenProvider) {
    return new ExportApiClientFactory(clientConfig, tokenProvider);
  }

  private static String resourcePath(Resource resource) {
    if (resource == null || !resource.exists()) {
      return null;
    }
    try {
      return resource.getURL().toString();
    } catch (IOException e) {
      try {
        return resource.getFile().getAbsolutePath();
      } catch (IOException nested) {
        return null;
      }
    }
  }

  @Bean
  public ExportDelegate exportDelegate(SwatchExportApi exportApi) {
    return new ExportDelegateImpl(exportApi);
  }
}
