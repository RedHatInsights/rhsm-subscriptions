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

import java.io.FileNotFoundException;
import java.util.Map;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

class CertInfoEndpointTest {
  public static final char[] STORE_PASSWORD = "password".toCharArray();

  private RhsmApiProperties config;

  @BeforeEach
  private void setUp() throws FileNotFoundException {
    config = new RhsmApiProperties();
    config.setKeystore(ResourceUtils.getFile("classpath:client.jks"));
    config.setKeystorePassword(STORE_PASSWORD);
    config.setTruststore(ResourceUtils.getFile("classpath:test-ca.jks"));
    config.setTruststorePassword(STORE_PASSWORD);
  }

  @Test
  void loadStoreInfo() throws Exception {
    CertInfoEndpoint endpoint = new CertInfoEndpoint(config);
    Map<String, Map<String, String>> infoMap = endpoint.keystoreInfo();

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
}
