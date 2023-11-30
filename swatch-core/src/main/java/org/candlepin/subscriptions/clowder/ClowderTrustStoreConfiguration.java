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
package org.candlepin.subscriptions.clowder;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Logic copied from <a
 * href="https://github.com/RedHatInsights/clowder-quarkus-config-source/blob/main/src/main/java/com/redhat/cloud/common/clowder/configsource/ClowderConfigSource.java#L506">ClowderConfigSource
 * for Quarkus</a>.
 */
@Slf4j
@Getter
public class ClowderTrustStoreConfiguration {

  public static final String CLOWDER_ENDPOINT_STORE_TYPE = "PKCS12";

  // Fixed length for the password used - it could probably be anything else - but ran my tests on a
  // FIPS environment with these.
  private static final int DEFAULT_PASSWORD_LENGTH = 33;
  private static final String CERTIFICATE_TEMP_FILENAME = "truststore";
  private static final String CERTIFICATE_TEMP_SUFFIX = ".trust";

  private final String path;
  private final String password;

  public ClowderTrustStoreConfiguration(String certPath) {
    try {
      String certContent = Files.readString(new File(certPath).toPath(), UTF_8);
      List<String> base64Certs = readCerts(certContent);

      List<X509Certificate> certificates =
          parsePemCert(base64Certs).stream().map(this::buildX509Cert).toList();

      if (certificates.isEmpty()) {
        throw new IllegalStateException("Could not parse any certificate in the file");
      }

      // Generate a keystore by hand
      // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html

      KeyStore truststore = KeyStore.getInstance(CLOWDER_ENDPOINT_STORE_TYPE);

      // Per the docs, we need to init the new keystore with load(null)
      truststore.load(null);

      for (int i = 0; i < certificates.size(); i++) {
        truststore.setCertificateEntry("cert-" + i, certificates.get(i));
      }

      char[] passwordAsArray = buildPassword(base64Certs.get(0));
      this.path = writeTruststore(truststore, passwordAsArray);
      this.password = new String(passwordAsArray);
    } catch (IOException ioe) {
      throw new IllegalStateException(
          "Couldn't load the certificate, but we were requested a truststore", ioe);
    } catch (KeyStoreException kse) {
      throw new IllegalStateException("Couldn't load the keystore format PKCS12", kse);
    } catch (NoSuchAlgorithmException | CertificateException ce) {
      throw new IllegalStateException("Couldn't configure the keystore", ce);
    }
  }

  private List<String> readCerts(String certString) {
    return Arrays.stream(certString.split("-----BEGIN CERTIFICATE-----"))
        .filter(s -> !s.isEmpty())
        .map(
            s ->
                Arrays.stream(s.split("-----END CERTIFICATE-----"))
                    .filter(s2 -> !s2.isEmpty())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Invalid certificate found")))
        .map(String::trim)
        .map(s -> s.replace("\n", ""))
        .toList();
  }

  private List<byte[]> parsePemCert(List<String> base64Certs) {
    return base64Certs.stream()
        .map(cert -> Base64.getDecoder().decode(cert.getBytes(StandardCharsets.UTF_8)))
        .toList();
  }

  private X509Certificate buildX509Cert(byte[] cert) {
    try {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(cert));
    } catch (CertificateException certificateException) {
      throw new IllegalStateException(
          "Couldn't load the x509 certificate factory", certificateException);
    }
  }

  private char[] buildPassword(String seed) {
    // To avoid having a fixed password - fetch the first characters from a string (the certificate)
    int size = Math.min(DEFAULT_PASSWORD_LENGTH, seed.length());
    char[] passwordAsArray = new char[size];
    seed.getChars(0, size, passwordAsArray, 0);
    return passwordAsArray;
  }

  private String writeTruststore(KeyStore keyStore, char[] password) {
    try {
      File file = createTempFile();
      keyStore.store(new FileOutputStream(file), password);
      return file.getAbsolutePath();
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new IllegalStateException("Truststore creation failed", e);
    }
  }

  private File createTempFile() throws IOException {
    File file = File.createTempFile(CERTIFICATE_TEMP_FILENAME, CERTIFICATE_TEMP_SUFFIX);
    try {
      file.deleteOnExit();
    } catch (SecurityException e) {
      log.warn(
          "Delete on exit of the '{}' cert file denied by the security manager", file.getName(), e);
    }
    return file;
  }
}
