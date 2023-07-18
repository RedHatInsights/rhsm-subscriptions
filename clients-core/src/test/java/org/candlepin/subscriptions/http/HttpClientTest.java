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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;

class HttpClientTest {
  public static final String STORE_PASSWORD = "password";

  private WireMockServer server;
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

  private Resource getKeystoreResource(String f) {
    return rl.getResource("file:" + f);
  }

  @Test
  void testNoCustomTruststoreRequired() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    HttpClientProperties x509Config = new HttpClientProperties();
    x509Config.setKeystore(getKeystoreResource(server.getOptions().httpsSettings().keyStorePath()));
    x509Config.setKeystorePassword(STORE_PASSWORD.toCharArray());

    Client httpClient = HttpClient.buildHttpClient(x509Config, null, false);

    ProcessingException e = assertThrows(ProcessingException.class, () -> invokeHello(httpClient));
    // We should get a handshake exception since the Wiremock server is using a cert signed by a
    // self-signed CA that isn't in the default Java truststore.  We actually would like to test
    // that a certificate signed by a legitimate CA gets accepted, but that would require us to have
    // a legitimate server certificate and key for the Wiremock server to use.
    assertThat(
        e.getCause().getMessage(),
        Matchers.containsString("unable to find valid certification path"));
  }

  @Test
  void testTlsClientAuth() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    HttpClientProperties x509Config = new HttpClientProperties();
    x509Config.setKeystore(getKeystoreResource(server.getOptions().httpsSettings().keyStorePath()));
    x509Config.setKeystorePassword(STORE_PASSWORD.toCharArray());

    x509Config.setTruststore(rl.getResource("classpath:test-ca.jks"));
    x509Config.setTruststorePassword(STORE_PASSWORD.toCharArray());

    Client httpClient = HttpClient.buildHttpClient(x509Config, null, false);

    assertEquals("Hello World", invokeHello(httpClient));
  }

  @Test
  void testTlsClientAuthFailsWithNoClientCert() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    HttpClientProperties x509Config = new HttpClientProperties();
    x509Config.setTruststore(rl.getResource("classpath:test-ca.jks"));
    x509Config.setTruststorePassword(STORE_PASSWORD.toCharArray());

    Client httpClient = HttpClient.buildHttpClient(x509Config, null, false);

    // NOTE: openjdk behavior changed w/ https://bugs.openjdk.java.net/browse/JDK-8263435
    // 11.0.12 onwards produces a cause of SocketException, older produces SSLException,
    // Using IOException (superclass of both) makes the test less brittle
    ProcessingException e = assertThrows(ProcessingException.class, () -> invokeHello(httpClient));
    assertThat(e.getCause(), instanceOf(IOException.class));
  }

  private String invokeHello(Client client) throws IOException {
    return client
        .target(UriBuilder.fromUri(server.url("/hello")))
        .request(MediaType.TEXT_PLAIN)
        .buildGet()
        .invoke(String.class);
  }

  private WireMockConfiguration buildWireMockConfig() throws FileNotFoundException {
    String keystorePath = ResourceUtils.getFile("classpath:server.jks").getPath();
    String truststorePath = ResourceUtils.getFile("classpath:test-ca.jks").getPath();
    return WireMockConfiguration.options()
        .dynamicHttpsPort()
        .dynamicPort()
        .keystorePath(keystorePath)
        .keystorePassword(STORE_PASSWORD)
        .needClientAuth(true)
        .trustStorePath(truststorePath)
        .trustStorePassword(STORE_PASSWORD);
  }
}
