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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class HttpClientTest {

  private WireMockServer server;

  private MappingBuilder stubHelloWorld() {
    return get(urlPathEqualTo("/hello"))
        .willReturn(ok("Hello World").withHeader("Content-Type", "text/plain"));
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void testRestTemplateCreation() throws Exception {
    HttpClientProperties config = new HttpClientProperties();
    config.setUrl("https://example.com");

    RestTemplate restTemplate = SpringHttpClientBuilder.buildRestTemplate(config);

    assertNotNull(restTemplate);
    assertNotNull(restTemplate.getRequestFactory());
  }

  @Test
  void testBasicHttpCall() throws Exception {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    server.stubFor(stubHelloWorld());

    HttpClientProperties config = new HttpClientProperties();
    config.setUrl(server.url("/"));

    RestTemplate restTemplate = SpringHttpClientBuilder.buildRestTemplate(config);
    String response = restTemplate.getForObject(server.url("/hello"), String.class);

    assertEquals("Hello World", response);
  }
}
