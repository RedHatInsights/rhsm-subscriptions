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
package org.candlepin.insights.pinhead.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.io.Resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.GenericType;

public class X509ApiClientFactoryTest {
    public static final String STORE_PASSWORD = "password";

    private WireMockServer server;

    private MappingBuilder stubHelloWorld() {
        return get(urlPathEqualTo("/hello")).willReturn(
            ok("Hello World").withHeader("Content-Type", "text/plain")
        );
    }

    @AfterEach
    private void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testTlsClientAuth() throws Exception {
        server = new WireMockServer(buildWireMockConfig());
        server.start();
        server.stubFor(stubHelloWorld());

        X509ApiClientFactoryConfiguration x509Config = new X509ApiClientFactoryConfiguration();
        x509Config.setKeystoreFile(server.getOptions().httpsSettings().keyStorePath());
        x509Config.setKeystorePassword(STORE_PASSWORD);

        x509Config.setTruststoreFile(Resources.getResource("test-ca.jks").getPath());
        x509Config.setTruststorePassword(STORE_PASSWORD);

        X509ApiClientFactory factory = new X509ApiClientFactory(x509Config);
        ApiClient client = factory.getObject();

        client.setBasePath(server.baseUrl());
        assertEquals("Hello World", invokeHello(client));
    }

    @Test
    public void testTlsClientAuthFailsWithNoClientCert() throws Exception {
        server = new WireMockServer(buildWireMockConfig());
        server.start();
        server.stubFor(stubHelloWorld());

        X509ApiClientFactoryConfiguration x509Config = new X509ApiClientFactoryConfiguration();
        x509Config.setTruststoreFile(Resources.getResource("test-ca.jks").getPath());
        x509Config.setTruststorePassword(STORE_PASSWORD);

        X509ApiClientFactory factory = new X509ApiClientFactory(x509Config);
        ApiClient client = factory.getObject();

        client.setBasePath(server.baseUrl());
        Exception e = assertThrows(ProcessingException.class, () -> invokeHello(client));
        assertThat(e.getCause(), instanceOf(SSLHandshakeException.class));
    }

    /** Since the method call for invokeApi is so messy, let's encapsulate it here. */
    private String invokeHello(ApiClient client) throws ApiException {
        return client.<String>invokeAPI(
            "/hello",
            "GET",
            new ArrayList<>(),
            new Object(),
            new HashMap<>(),
            new HashMap<>(),
            "text/plain",
            "text/plain",
            new String[] {},
            new GenericType<String>() {}
        );
    }

    private WireMockConfiguration buildWireMockConfig() {
        String keystorePath = Resources.getResource("server.jks").getPath();
        String truststorePath = Resources.getResource("test-ca.jks").getPath();
        return WireMockConfiguration.options()
            .dynamicHttpsPort()
            .dynamicPort()
            .keystorePath(keystorePath)
            .keystorePassword(STORE_PASSWORD)
            .needClientAuth(true)
            .trustStorePath(truststorePath)
            .trustStorePassword(STORE_PASSWORD);
    }
}
