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
package org.candlepin.subscriptions.conduit.rhsm.client;

import java.io.IOException;
import java.io.InputStream;
import javax.net.ssl.HostnameVerifier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Class to hold values used to build the ApiClient instance wrapped in an SSLContext for RHSM. */
@Getter
@Setter
public class RhsmApiProperties {
  @Setter(AccessLevel.NONE)
  private X509ApiClientFactoryConfiguration x509Config = new X509ApiClientFactoryConfiguration();

  private boolean useStub;
  private String url;
  private int requestBatchSize = 100;

  public String getKeystorePassword() {
    return x509Config.getKeystorePassword();
  }

  public void setKeystorePassword(String keystorePassword) {
    x509Config.setKeystorePassword(keystorePassword);
  }

  public String getTruststorePassword() {
    return x509Config.getTruststorePassword();
  }

  public void setTruststorePassword(String truststorePassword) {
    x509Config.setTruststorePassword(truststorePassword);
  }

  public String getKeystoreFile() {
    return x509Config.getKeystoreFile();
  }

  public void setKeystoreFile(String keystoreFile) {
    x509Config.setKeystoreFile(keystoreFile);
  }

  public String getTruststoreFile() {
    return x509Config.getTruststoreFile();
  }

  public void setTruststoreFile(String truststoreFile) {
    x509Config.setTruststoreFile(truststoreFile);
  }

  public InputStream getKeystoreStream() throws IOException {
    return x509Config.getKeystoreStream();
  }

  public InputStream getTruststoreStream() throws IOException {
    return x509Config.getTruststoreStream();
  }

  public HostnameVerifier getHostnameVerifier() {
    return x509Config.getHostnameVerifier();
  }

  public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
    x509Config.setHostnameVerifier(hostnameVerifier);
  }

  public boolean usesClientAuth() {
    return x509Config.usesClientAuth();
  }

  public void setMaxConnections(int maxConnections) {
    x509Config.setMaxConnections(maxConnections);
  }
}
