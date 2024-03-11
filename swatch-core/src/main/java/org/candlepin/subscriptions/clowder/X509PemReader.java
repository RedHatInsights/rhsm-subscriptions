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

import jakarta.xml.bind.DatatypeConverter;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.binary.Base64;

public class X509PemReader {

  public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  public static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

  private final String pem;

  public X509PemReader(File file) throws IOException {
    this(new BufferedInputStream(new FileInputStream(file)));
  }

  public X509PemReader(InputStream inputStream) throws IOException {
    this.pem = new String(inputStream.readAllBytes(), Charset.defaultCharset());
  }

  protected List<X509Certificate> parsePem() throws CertificateException {
    var certs = new ArrayList<X509Certificate>();
    var certificateFactory = CertificateFactory.getInstance("X.509");

    var singleLinePem = pem.replace(System.lineSeparator(), "");
    var splitPem = singleLinePem.split(BEGIN_CERTIFICATE);

    for (String cert : splitPem) {
      if (cert.isEmpty()) {
        /* There's a fence post problem with splitting on BEGIN_CERTIFICATE.
         * That token is usually the first line of PEM bundle, so the first item
         * in our list of split certificates ends up being the empty string */
        continue;
      }

      String rawPem = cert.replace(END_CERTIFICATE, "");
      byte[] encoded = Base64.decodeBase64(rawPem);

      var c = certificateFactory.generateCertificate(new ByteArrayInputStream(encoded));
      certs.add((X509Certificate) c);
    }

    return certs;
  }

  public List<CertInfo> readCerts() throws GeneralSecurityException {
    var certs = parsePem();
    var certInfos = new ArrayList<CertInfo>();

    for (X509Certificate cert : certs) {
      CertInfo ci = new CertInfo();
      String dn = cert.getSubjectX500Principal().getName();
      ci.setDistinguishedName(dn);
      ci.setSerialNumber(cert.getSerialNumber().toString());
      ci.setNotBefore(X509PemReader.certDate(cert.getNotBefore()));
      ci.setNotAfter(X509PemReader.certDate(cert.getNotAfter()));
      ci.setSha1Fingerprint(X509PemReader.getFingerprint(cert));
      certInfos.add(ci);
    }

    return certInfos;
  }

  public static String getFingerprint(X509Certificate certificate)
      throws NoSuchAlgorithmException, CertificateEncodingException {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    md.update(certificate.getEncoded());
    return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
  }

  public static String certDate(Date date) {
    return DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
  }

  @Getter
  @Setter
  public static class CertInfo {
    private String distinguishedName;
    private String serialNumber;
    private String sha1Fingerprint;
    private String notAfter;
    private String notBefore;

    @Override
    public String toString() {
      return String.format(
          "Certificate %s (SHA1: %s, Serial: %s) Not Before %s, Not After %s",
          distinguishedName, sha1Fingerprint, serialNumber, notBefore, notAfter);
    }
  }
}
