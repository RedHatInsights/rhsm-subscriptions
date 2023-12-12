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
package org.candlepin.subscriptions.actuator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.candlepin.subscriptions.util.TlsProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CertInfoContributorTest.Config.class})
class CertInfoContributorTest {
  public static final char[] STORE_PASSWORD = "password".toCharArray();

  private final ResourceLoader rl = new DefaultResourceLoader();
  private TlsProperties config;

  private CertInfoContributor infoContributor;

  private Map<String, Map<String, String>> infoMap;

  @Autowired private ApplicationContext context;

  public static class Config {
    @Bean
    public ResourceLoader resourceLoader() {
      return new DefaultResourceLoader();
    }

    @Bean
    public TlsProperties goodConfig() {
      TlsProperties good = new TlsProperties();
      good.setKeystore(resourceLoader().getResource("classpath:client.jks"));
      good.setKeystorePassword(STORE_PASSWORD);
      good.setTruststore(resourceLoader().getResource("classpath:test-ca.jks"));
      good.setTruststorePassword(STORE_PASSWORD);
      return good;
    }

    @Bean
    public TlsProperties badConfig() {
      TlsProperties bad = new TlsProperties();
      bad.setKeystore(resourceLoader().getResource("classpath:not-exist"));
      bad.setKeystorePassword(STORE_PASSWORD);
      bad.setTruststore(resourceLoader().getResource("classpath:not-exist"));
      bad.setTruststorePassword(STORE_PASSWORD);
      return bad;
    }

    @Bean
    public TlsProperties emptyConfig() {
      TlsProperties empty = new TlsProperties();
      return empty;
    }
  }

  @BeforeEach
  void setUp() {
    infoMap = null;
    config = new TlsProperties();

    infoContributor = new CertInfoContributor(context);
  }

  @Test
  // Note that this is an integration type test to make sure the CertInfoContributor scans through
  // all the TlsProperties beans
  @SuppressWarnings("unchecked")
  void testBeanIntrospection() {
    Builder builder = new Builder();
    infoContributor.contribute(builder);
    Info info = builder.build();

    System.out.println(info.getDetails());

    assertAll(
        () -> {
          var goodConfig = (Map<String, Object>) info.getDetails().get("goodConfig.keystore");
          assertThat(goodConfig, Matchers.hasKey("client"));
          var details = (Map<String, String>) goodConfig.get("client");
          assertEquals("CN=Client", details.get("Distinguished Name"));
        },
        () -> {
          var goodConfig = (Map<String, Object>) info.getDetails().get("goodConfig.truststore");
          assertThat(goodConfig, Matchers.hasKey("test ca"));
          var details = (Map<String, String>) goodConfig.get("test ca");
          assertEquals("CN=Test CA", details.get("Distinguished Name"));
        });

    assertAll(
        () -> {
          var badConfig = (Map<String, Object>) info.getDetails().get("badConfig.keystore");
          assertThat(badConfig, Matchers.hasKey("not-exist not readable"));
        },
        () -> {
          var badConfig = (Map<String, Object>) info.getDetails().get("badConfig.keystore");
          assertThat(badConfig, Matchers.hasKey("not-exist not readable"));
        });

    assertAll(
        () -> {
          var emptyConfig = (Map<String, Object>) info.getDetails().get("emptyConfig.keystore");
          assertEquals(0, emptyConfig.size());
        },
        () -> {
          var emptyConfig = (Map<String, Object>) info.getDetails().get("emptyConfig.keystore");
          assertEquals(0, emptyConfig.size());
        });
  }

  @Test
  void testBadCertificateKeystoreInfo() {
    givenKeystoreDoesNotExistInConfig();
    whenCallKeystoreInfo();
    assertThat(infoMap, Matchers.hasKey("not-exist not readable"));
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
  void testBadCertificateTruststoreInfo() {
    givenTruststoreDoesNotExistInConfig();
    whenCallTruststoreInfo();
    assertThat(infoMap, Matchers.hasKey("not-exist not readable"));
  }

  @Test
  void testEmptyCertificateTruststoreInfo() {
    givenTruststoreEmptyInConfig();
    whenCallTruststoreInfo();
    assertTrue(infoMap.isEmpty());
  }

  @Test
  void testNullCertificateTruststoreInfo() {
    givenTruststoreNullInConfig();
    whenCallTruststoreInfo();
    assertTrue(infoMap.isEmpty());
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

  private void givenTruststoreEmptyInConfig() {
    config.setTruststore(rl.getResource(""));
    config.setTruststorePassword("".toCharArray());
  }

  private void givenTruststoreNullInConfig() {
    config.setTruststore(null);
    config.setTruststorePassword(null);
  }

  private void whenCallKeystoreInfo() {
    infoMap = infoContributor.storeInfo(config.getKeystore(), config.getKeystorePassword());
  }

  private void whenCallTruststoreInfo() {
    infoMap = infoContributor.storeInfo(config.getTruststore(), config.getTruststorePassword());
  }
}
