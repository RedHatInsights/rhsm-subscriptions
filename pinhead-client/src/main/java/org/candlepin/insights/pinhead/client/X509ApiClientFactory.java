/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.pinhead.client;

import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.springframework.beans.factory.FactoryBean;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * Extends the generated ApiClient class to allow for X509 Client Certificate authentication
 */
public class X509ApiClientFactory implements FactoryBean<ApiClient>  {
    private final X509ApiClientFactoryConfiguration x509Config;

    public X509ApiClientFactory(X509ApiClientFactoryConfiguration x509Config) {
        this.x509Config = x509Config;
    }

    @Override
    public ApiClient getObject() throws Exception {
        ApiClient client = Configuration.getDefaultApiClient();
        client.setHttpClient(buildHttpClient(x509Config, client));
        return client;
    }

    @Override
    public Class<?> getObjectType() {
        return ApiClient.class;
    }

    private Client buildHttpClient(X509ApiClientFactoryConfiguration x509Config, ApiClient client)
        throws GeneralSecurityException {
        ClientConfiguration clientConfig = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        clientConfig.register(client.getJSON());
        if (client.isDebugging()) {
            clientConfig.register(Logger.class);
        }

        ClientBuilder builder = ClientBuilder.newBuilder().withConfig(clientConfig);
        builder.hostnameVerifier(x509Config.getHostnameVerifier());

        try {
            KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            truststore.load(
                x509Config.getTruststoreStream(), x509Config.getTruststorePassword().toCharArray()
            );
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            trustFactory.init(truststore);

            KeyManager[] keyManagers = null;
            if (x509Config.usesClientAuth()) {
                KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(x509Config.getKeystoreStream(), x509Config.getKeystorePassword().toCharArray());
                keyFactory.init(keystore, x509Config.getKeystorePassword().toCharArray());
                keyManagers = keyFactory.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustFactory.getTrustManagers(), null);
            builder.sslContext(sslContext);
        }
        catch (KeyStoreException | NoSuchAlgorithmException | IOException e)  {
            throw new GeneralSecurityException("Failed to init SSLContext", e);
        }

        return builder.build();
    }
}
