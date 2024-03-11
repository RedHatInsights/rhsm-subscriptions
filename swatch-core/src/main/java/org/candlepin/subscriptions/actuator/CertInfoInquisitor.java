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

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.candlepin.subscriptions.clowder.X509PemReader;
import org.springframework.core.io.Resource;

/** Class to return basic information about an X509 certificate. */
public class CertInfoInquisitor {
  private CertInfoInquisitor() {
    // Static methods only
  }

  public static Map<String, Map<String, String>> loadStoreInfo(Resource resource, char[] password)
      throws GeneralSecurityException, IOException {
    return CertInfoInquisitor.loadStoreInfo(resource.getInputStream(), password);
  }

  public static Map<String, Map<String, String>> loadStoreInfo(InputStream stream, char[] password)
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
      aliasInfo.put("Distinguished Name", certificate.getSubjectX500Principal().getName());
      aliasInfo.put("Serial Number", certificate.getSerialNumber().toString());
      aliasInfo.put("SHA-1 Fingerprint", X509PemReader.getFingerprint(certificate));
      aliasInfo.put("Not After", X509PemReader.certDate(certificate.getNotAfter()));
      aliasInfo.put("Issuer Distinguished Name", certificate.getIssuerX500Principal().getName());
    }

    return ksInfoMap;
  }
}
