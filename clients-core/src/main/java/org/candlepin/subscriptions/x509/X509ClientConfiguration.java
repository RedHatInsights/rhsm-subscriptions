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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.net.ssl.HostnameVerifier;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;

/** Class to hold values used to build the ApiClient instance wrapped in an SSLContext. */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class X509ClientConfiguration {
  @ToString.Include private String keystoreFile;
  @ToString.Include private String truststoreFile;
  private String keystorePassword;
  private String truststorePassword;

  /**
   * -- SETTER -- Allow setting the HostnameVerifier implementation. NoopHostnameVerifier could be
   * used in testing, for example
   */
  private HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();

  public InputStream getKeystoreStream() throws IOException {
    if (keystoreFile == null) {
      throw new IllegalStateException("No keystore file has been set");
    }
    return readStream(keystoreFile);
  }

  public InputStream getTruststoreStream() throws IOException {
    if (truststoreFile == null) {
      throw new IllegalStateException("No truststore file has been set");
    }
    return readStream(truststoreFile);
  }

  private InputStream readStream(String path) throws IOException {
    return new ByteArrayInputStream(Files.readAllBytes(Paths.get(path)));
  }

  public boolean usesClientAuth() {
    return (getKeystoreFile() != null
        && !getKeystoreFile().isEmpty()
        && getKeystorePassword() != null);
  }

  public boolean usesDefaultTruststore() {
    return getTruststoreFile() == null || getTruststoreFile().isBlank();
  }
}
