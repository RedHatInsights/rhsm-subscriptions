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
package org.candlepin.subscriptions.conduit.rhsm.client;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.io.Resources;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.GenericType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class RhsmApiFactoryTest {
  public static final char[] STORE_PASSWORD = "password".toCharArray();

  private WireMockServer server;
  private RhsmApiProperties config;
  private RhsmApiFactory apiFactory;
  private ResourceLoader rl = new DefaultResourceLoader();

  private MappingBuilder stubHelloWorld() {
    return get(urlPathEqualTo("/hello"))
        .willReturn(ok("Hello World").withHeader("Content-Type", "text/plain"));
  }

  @AfterEach
  private void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @BeforeEach
  private void setUp() {
    config = new RhsmApiProperties();
  }

  private Resource getKeystoreResource(String f) {
    return rl.getResource("file:" + f);
  }

  @Test
  void testStubClientConfiguration() throws Exception {
    config.setUseStub(true);
    RhsmApiFactory factory = new RhsmApiFactory(config);
    assertEquals(StubRhsmApi.class, factory.getObject().getClass());
  }

  @Test
  void testNoAuthClientConfiguration() throws Exception {
    RhsmApiFactory factory = new RhsmApiFactory(config);
    assertEquals(null, factory.getObject().getApiClient().getHttpClient().getSslContext());
  }

  @Test
  void testTlsClientAuth() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    config.setKeystore(getKeystoreResource(server.getOptions().httpsSettings().keyStorePath()));
    config.setKeystorePassword(STORE_PASSWORD);

    config.setTruststore(rl.getResource("classpath:test-ca.jks"));
    config.setTruststorePassword(STORE_PASSWORD);

    RhsmApiFactory factory = new RhsmApiFactory(config);
    ApiClient client = factory.getObject().getApiClient();

    client.setBasePath(server.baseUrl());
    assertEquals("Hello World", invokeHello(client));
  }

  @Test
  void testTlsClientAuthFailsWithNoClientCert() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    config.setTruststore(rl.getResource("classpath:test-ca.jks"));
    config.setTruststorePassword(STORE_PASSWORD);

    RhsmApiFactory factory = new RhsmApiFactory(config);
    ApiClient client = factory.getObject().getApiClient();

    client.setBasePath(server.baseUrl());

    // NOTE: openjdk behavior changed w/ https://bugs.openjdk.java.net/browse/JDK-8263435
    // 11.0.12 onwards produces a cause of SocketException, older produces SSLException,
    // Using IOException (superclass of both) makes the test less brittle
    ProcessingException e = assertThrows(ProcessingException.class, () -> invokeHello(client));
    assertThat(e.getCause(), instanceOf(IOException.class));
  }

  /** Since the method call for invokeApi is so messy, let's encapsulate it here. */
  private String invokeHello(ApiClient client) throws ApiException {
    return client.<String>invokeAPI(
        "/hello",
        "GET",
        new ArrayList<>(),
        new Object(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        "text/plain",
        "text/plain",
        new String[] {},
        new GenericType<String>() {});
  }

  private WireMockConfiguration buildWireMockConfig() {
    String keystorePath = Resources.getResource("server.jks").getPath();
    String truststorePath = Resources.getResource("test-ca.jks").getPath();
    return WireMockConfiguration.options()
        .dynamicHttpsPort()
        .dynamicPort()
        .keystorePath(keystorePath)
        .keystorePassword(new String(STORE_PASSWORD))
        .needClientAuth(true)
        .trustStorePath(truststorePath)
        .trustStorePassword(new String(STORE_PASSWORD));
  }
}
