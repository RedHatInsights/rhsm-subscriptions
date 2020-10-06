/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.rhsm.client;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;
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
import javax.net.ssl.TrustManager;
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
        HttpClientBuilder apacheBuilder = HttpClientBuilder.create();
        apacheBuilder.setSSLHostnameVerifier(x509Config.getHostnameVerifier());

        try {
            TrustManager[] trustManagers = null;
            if (!x509Config.usesDefaultTruststore()) {
                KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
                truststore.load(x509Config.getTruststoreStream(),
                    x509Config.getTruststorePassword().toCharArray());
                TrustManagerFactory trustFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustFactory.init(truststore);
                trustManagers = trustFactory.getTrustManagers();
            }

            KeyManager[] keyManagers = null;
            if (x509Config.usesClientAuth()) {
                KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm()
                );
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(x509Config.getKeystoreStream(), x509Config.getKeystorePassword().toCharArray());
                keyFactory.init(keystore, x509Config.getKeystorePassword().toCharArray());
                keyManagers = keyFactory.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustManagers, null);
            apacheBuilder.setSSLContext(sslContext);
        }
        catch (KeyStoreException | NoSuchAlgorithmException | IOException e)  {
            throw new GeneralSecurityException("Failed to init SSLContext", e);
        }

        RequestConfig cookieConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        apacheBuilder.setDefaultRequestConfig(cookieConfig);
        // Bump the max connections so that our task processors do not
        // block waiting to connect to RHSM.

        // note that these are essentially the same, since we're only hitting a single hostname
        apacheBuilder.setMaxConnPerRoute(x509Config.getMaxConnections());
        apacheBuilder.setMaxConnTotal(x509Config.getMaxConnections());
        HttpClient httpClient = apacheBuilder.build();

        // We've now constructed a basic Apache HttpClient.  Now we wire that in to RestEasy.  There is a
        // lot of overlap in the names and in the classes across Apache's http-components, Resteasy, and
        // the JAX-RS API.

        ClientHttpEngine engine = ApacheHttpClient4EngineFactory.create(httpClient);

        ClientConfiguration clientConfig = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        clientConfig.register(client.getJSON());
        if (client.isDebugging()) {
            clientConfig.register(Logger.class);
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

        return ((ResteasyClientBuilder) clientBuilder).httpEngine(engine).build();
    }
}
