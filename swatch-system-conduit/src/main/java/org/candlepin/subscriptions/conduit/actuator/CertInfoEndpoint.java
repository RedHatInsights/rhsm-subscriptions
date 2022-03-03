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

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.candlepin.subscriptions.conduit.rhsm.client.X509ApiClientFactoryConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Endpoint to print basic information about the certificates Conduit is using. */
@Component
@Endpoint(id = "certs")
public class CertInfoEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CertInfoEndpoint.class);
  public static final String CERT_LOAD_ERR = "Could not load certificates";

  private final RhsmApiProperties rhsmApiProperties;

  public CertInfoEndpoint(RhsmApiProperties rhsmApiProperties) {
    this.rhsmApiProperties = rhsmApiProperties;
  }

  @ReadOperation
  public Map<String, Map<String, String>> keystoreInfo() throws IllegalStateException {
    X509ApiClientFactoryConfiguration x509Config = rhsmApiProperties.getX509Config();

    try {
      return loadStoreInfo(
          x509Config.getKeystoreStream(), x509Config.getKeystorePassword().toCharArray());
    } catch (IOException | GeneralSecurityException e) {
      log.error(CERT_LOAD_ERR, e);
    }
    throw new IllegalStateException(CERT_LOAD_ERR);
  }

  @ReadOperation
  public Map<String, Map<String, String>> truststoreInfo() throws IllegalStateException {
    X509ApiClientFactoryConfiguration x509Config = rhsmApiProperties.getX509Config();

    try {
      return loadStoreInfo(
          x509Config.getTruststoreStream(), x509Config.getTruststorePassword().toCharArray());
    } catch (IOException | GeneralSecurityException e) {
      log.error(CERT_LOAD_ERR, e);
    }
    throw new IllegalStateException(CERT_LOAD_ERR);
  }

  protected Map<String, Map<String, String>> loadStoreInfo(InputStream stream, char[] password)
      throws GeneralSecurityException, IOException {
    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
    keystore.load(stream, password);

    Map<String, Map<String, String>> ksInfoMap = new HashMap<>();

    Enumeration<String> ksAliases = keystore.aliases();

    while (ksAliases.hasMoreElements()) {
      // Preserve insertion order
      Map<String, String> aliasInfo = new LinkedHashMap<>();
      String alias = ksAliases.nextElement();
      ksInfoMap.put(alias, aliasInfo);

      X509Certificate certificate = (X509Certificate) keystore.getCertificate(alias);
      aliasInfo.put("Distinguished Name", certificate.getSubjectDN().toString());
      aliasInfo.put("Serial Number", certificate.getSerialNumber().toString());
      aliasInfo.put("SHA-1 Fingerprint", getFingerprint(certificate));

      Instant notAfter = certificate.getNotAfter().toInstant();
      aliasInfo.put("Not After", DateTimeFormatter.ISO_INSTANT.format(notAfter));
      aliasInfo.put("Issuer Distinguished Name", certificate.getIssuerDN().toString());
    }

    return ksInfoMap;
  }

  protected String getFingerprint(X509Certificate certificate)
      throws NoSuchAlgorithmException, CertificateEncodingException {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    md.update(certificate.getEncoded());
    return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
  }
}
