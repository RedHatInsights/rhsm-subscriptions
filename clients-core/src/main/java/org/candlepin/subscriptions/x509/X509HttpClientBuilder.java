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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Constructs and Apache HttpClient configured to use the keystore and truststore set in the
 * X509ClientConfiguration object sent into the constructor.
 */
public class X509HttpClientBuilder {
  private final X509ClientConfiguration x509Config;

  public X509HttpClientBuilder(X509ClientConfiguration x509Config) {
    this.x509Config = x509Config;
  }

  public HttpClientBuilder createHttpClientBuilderForTls() throws GeneralSecurityException {
    HttpClientBuilder apacheBuilder = HttpClientBuilder.create();
    apacheBuilder.setSSLHostnameVerifier(x509Config.getHostnameVerifier());

    try {
      TrustManager[] trustManagers = null;
      if (!x509Config.usesDefaultTruststore()) {
        KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(
            x509Config.getTruststoreStream(), x509Config.getTruststorePassword().toCharArray());
        TrustManagerFactory trustFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(truststore);
        trustManagers = trustFactory.getTrustManagers();
      }

      KeyManager[] keyManagers = null;
      if (x509Config.usesClientAuth()) {
        KeyManagerFactory keyFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(
            x509Config.getKeystoreStream(), x509Config.getKeystorePassword().toCharArray());
        keyFactory.init(keystore, x509Config.getKeystorePassword().toCharArray());
        keyManagers = keyFactory.getKeyManagers();
      }

      SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(keyManagers, trustManagers, null);
      apacheBuilder.setSSLContext(sslContext);
    } catch (KeyStoreException | NoSuchAlgorithmException | IOException e) {
      throw new GeneralSecurityException("Failed to init SSLContext", e);
    }

    return apacheBuilder;
  }
}
