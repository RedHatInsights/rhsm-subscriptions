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
package org.candlepin.subscriptions.http;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/** Utility class for building Spring RestTemplate HTTP clients. */
@Slf4j
public class SpringHttpClientBuilder {
  private SpringHttpClientBuilder() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Build a Spring RestTemplate with customized connection settings.
   *
   * @param serviceProperties client configuration properties
   * @return RestTemplate with customized connection settings
   */
  public static RestTemplate buildRestTemplate(HttpClientProperties serviceProperties) {
    // Configure simple HTTP client factory for connection settings
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
    requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

    RestTemplate restTemplate = new RestTemplate();

    // Enable request/response buffering for debugging
    restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(requestFactory));

    // Add message converter for java.io.File uploads
    restTemplate.getMessageConverters().add(0, new FileHttpMessageConverter());

    // Disable default URL encoding - encode only parameter values
    DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
    restTemplate.setUriTemplateHandler(uriBuilderFactory);

    return restTemplate;
  }
}
