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
package org.candlepin.subscriptions.x509;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.net.ssl.SSLHandshakeException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

class X509HttpClientBuilderTest {
  public static final String STORE_PASSWORD = "password";

  private WireMockServer server;

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

  @Test
  void testNoCustomTruststoreRequired() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    X509ClientConfiguration x509Config = new X509ClientConfiguration();
    x509Config.setKeystoreFile(server.getOptions().httpsSettings().keyStorePath());
    x509Config.setKeystorePassword(STORE_PASSWORD);

    HttpClientBuilder clientBuilder =
        new X509HttpClientBuilder(x509Config).createHttpClientBuilderForTls();

    HttpClient httpClient = clientBuilder.build();

    SSLHandshakeException e =
        assertThrows(SSLHandshakeException.class, () -> invokeHello(httpClient));
    // We should get a handshake exception since the Wiremock server is using a cert signed by a
    // self-signed CA that isn't in the default Java truststore.  We actually would like to test
    // that a certificate signed by a legitimate CA gets accepted, but that would require us to have
    // a legitimate server certificate and key for the Wiremock server to use.
    assertThat(e.getMessage(), Matchers.containsString("unable to find valid certification path"));
  }

  @Test
  void testTlsClientAuth() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    X509ClientConfiguration x509Config = new X509ClientConfiguration();
    x509Config.setKeystoreFile(server.getOptions().httpsSettings().keyStorePath());
    x509Config.setKeystorePassword(STORE_PASSWORD);

    x509Config.setTruststoreFile(ResourceUtils.getFile("classpath:test-ca.jks").getPath());
    x509Config.setTruststorePassword(STORE_PASSWORD);

    HttpClientBuilder clientBuilder =
        new X509HttpClientBuilder(x509Config).createHttpClientBuilderForTls();

    HttpClient httpClient = clientBuilder.build();
    assertEquals("Hello World", invokeHello(httpClient));
  }

  @Test
  void testTlsClientAuthFailsWithNoClientCert() throws Exception {
    server = new WireMockServer(buildWireMockConfig());
    server.start();
    server.stubFor(stubHelloWorld());

    X509ClientConfiguration x509Config = new X509ClientConfiguration();
    x509Config.setTruststoreFile(ResourceUtils.getFile("classpath:test-ca.jks").getPath());
    x509Config.setTruststorePassword(STORE_PASSWORD);

    HttpClientBuilder clientBuilder =
        new X509HttpClientBuilder(x509Config).createHttpClientBuilderForTls();

    HttpClient httpClient = clientBuilder.build();
    // NOTE: openjdk behavior changed w/ https://bugs.openjdk.java.net/browse/JDK-8263435
    // 11.0.12 onwards produces a cause of SocketException, older produces SSLException,
    // Using IOException (superclass of both) makes the test less brittle
    Exception e = assertThrows(IOException.class, () -> invokeHello(httpClient));
    assertThat(e.getMessage(), Matchers.containsString("bad_certificate"));
  }

  private String invokeHello(HttpClient client) throws IOException {
    HttpUriRequest req =
        RequestBuilder.get(server.url("/hello"))
            .addHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
            .build();

    HttpResponse resp = client.execute(req);
    HttpEntity entity = resp.getEntity();
    return EntityUtils.toString(entity);
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
