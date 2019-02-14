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

import org.candlepin.insights.pinhead.client.resources.PinheadApi;


import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import javax.ws.rs.client.ClientBuilder;

/**
 * Builds a PinheadApi, which may be a stub, or a normal client, with or without cert auth depending on
 * config.
 */
public class PinheadApiFactory implements FactoryBean<PinheadApi>  {
    private static Logger log = LoggerFactory.getLogger(PinheadApiFactory.class);
    private final PinheadApiConfiguration config;

    public PinheadApiFactory(PinheadApiConfiguration config) {
        this.config = config;
    }

    @Override
    public PinheadApi getObject() throws Exception {
        if (config.isUseStub()) {
            log.info("Using stub pinhead client");
            return new StubPinheadApi();
        }
        ApiClient client;
        if (config.usesClientAuth()) {
            log.info("Pinhead client configured with client-cert auth");
            client = buildHttpsClient();
        }
        else {
            log.info("Pinhead client configured without client-cert auth");
            client = new ApiClient();
        }
        if (config.getUrl() != null) {
            log.info("Pinhead URL: {}", config.getUrl());
            client.setBasePath(config.getUrl());
        }
        else {
            log.warn("Pinhead URL not set...");
        }
        return new PinheadApi(client);
    }

    @Override
    public Class<?> getObjectType() {
        return PinheadApi.class;
    }

    private ApiClient buildHttpsClient() throws GeneralSecurityException {
        ApiClient client = Configuration.getDefaultApiClient();
        ClientConfiguration clientConfig = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        clientConfig.register(client.getJSON());
        if (client.isDebugging()) {
            clientConfig.register(org.jboss.logging.Logger.class);
        }

        ClientBuilder builder = ClientBuilder.newBuilder().withConfig(clientConfig);
        builder.hostnameVerifier(config.getHostnameVerifier());

        try {
            KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            truststore.load(
                config.getTruststoreStream(), config.getTruststorePassword().toCharArray()
            );
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            trustFactory.init(truststore);

            KeyManager[] keyManagers = null;
            if (config.usesClientAuth()) {
                KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(config.getKeystoreStream(), config.getKeystorePassword().toCharArray());
                keyFactory.init(keystore, config.getKeystorePassword().toCharArray());
                keyManagers = keyFactory.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustFactory.getTrustManagers(), null);
            builder.sslContext(sslContext);
        }
        catch (KeyStoreException | NoSuchAlgorithmException | IOException e)  {
            throw new GeneralSecurityException("Failed to init SSLContext", e);
        }

        client.setHttpClient(builder.build());
        return client;
    }
}
