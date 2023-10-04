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
package org.candlepin.subscriptions.conduit.actuator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class CertInfoEndpointTest {
  public static final char[] STORE_PASSWORD = "password".toCharArray();

  private final ResourceLoader rl = new DefaultResourceLoader();
  private RhsmApiProperties config;
  private CertInfoEndpoint endpoint;

  private Map<String, Map<String, String>> infoMap;

  @BeforeEach
  void setUp() {
    infoMap = null;

    config = new RhsmApiProperties();
    endpoint = new CertInfoEndpoint(config);
  }

  @Test
  void testThrowExceptionWhenNoCertificateKeystoreInfo() {
    givenKeystoreDoesNotExistInConfig();
    assertThrows(IllegalStateException.class, this::whenCallKeystoreInfo);
  }

  @Test
  void testKeystoreInfo() {
    givenKeystoreExistsInConfig();
    whenCallKeystoreInfo();
    // assertions
    assertThat(infoMap, Matchers.hasKey("client"));
    Map<String, String> certInfo = infoMap.get("client");
    assertAll(
        "Certificate",
        () -> assertEquals("CN=Client", certInfo.get("Distinguished Name")),
        () ->
            assertEquals(
                "278579299951850685400938987567493704619512184278", certInfo.get("Serial Number")),
        () ->
            assertEquals(
                "5253AE7B787839DF9F1C1A0E5FECB5F1C1868FAF",
                certInfo.get("SHA-1 Fingerprint").toUpperCase()),
        () -> assertEquals("CN=Test CA", certInfo.get("Issuer Distinguished Name")));
  }

  @Test
  void testThrowExceptionWhenNoCertificateTruststoreInfo() {
    givenTruststoreDoesNotExistInConfig();
    assertThrows(IllegalStateException.class, this::whenCallTruststoreInfo);
  }

  @Test
  void testTruststoreInfo() {
    givenTruststoreExistsInConfig();
    whenCallTruststoreInfo();
    // assertions
    assertThat(infoMap, Matchers.hasKey("test ca"));
    Map<String, String> certInfo = infoMap.get("test ca");
    assertAll(
        "Certificate",
        () -> assertEquals("CN=Test CA", certInfo.get("Distinguished Name")),
        () ->
            assertEquals(
                "409546735888018136828579902447384591449143398997", certInfo.get("Serial Number")),
        () ->
            assertEquals(
                "FCD529E0354E4D862E1641840D97279E7098C668",
                certInfo.get("SHA-1 Fingerprint").toUpperCase()),
        () -> assertEquals("CN=Test CA", certInfo.get("Issuer Distinguished Name")));
  }

  private void givenKeystoreDoesNotExistInConfig() {
    config.setKeystore(rl.getResource("classpath:not-exist"));
    config.setKeystorePassword(STORE_PASSWORD);
  }

  private void givenKeystoreExistsInConfig() {
    config.setKeystore(rl.getResource("classpath:client.jks"));
    config.setKeystorePassword(STORE_PASSWORD);
  }

  private void givenTruststoreDoesNotExistInConfig() {
    config.setTruststore(rl.getResource("classpath:not-exist"));
    config.setTruststorePassword(STORE_PASSWORD);
  }

  private void givenTruststoreExistsInConfig() {
    config.setTruststore(rl.getResource("classpath:test-ca.jks"));
    config.setTruststorePassword(STORE_PASSWORD);
  }

  private void whenCallKeystoreInfo() {
    infoMap = endpoint.keystoreInfo();
  }

  private void whenCallTruststoreInfo() {
    infoMap = endpoint.truststoreInfo();
  }
}
