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
package org.candlepin.rbac;

import org.candlepin.insights.rbac.client.ApiClient;
import org.candlepin.insights.rbac.client.RbacApi;
import org.candlepin.insights.rbac.client.RbacApiImpl;
import org.candlepin.insights.rbac.client.RbacServiceProperties;
import org.candlepin.insights.rbac.client.StubRbacApi;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * Factory that produces inventory service clients using configuration. An
 * AuthApiClient is used to ensure that the identity header is passed on
 * with any request to the RBAC API.
 */
public class RbacApiFactory implements FactoryBean<RbacApi> {

    private static Logger log = LoggerFactory.getLogger(RbacApiFactory.class);

    private final RbacServiceProperties serviceProperties;

    public RbacApiFactory(RbacServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    public RbacApi getObject() throws Exception {
        if (serviceProperties.isUseStub()) {
            log.info("Using stub RBAC client");
            return new StubRbacApi();
        }
        ApiClient apiClient = new RBACApiClient();
        apiClient.setHttpClient(buildHttpClient(apiClient));
        if (serviceProperties.getUrl() != null) {
            log.info("RBAC service URL: {}", serviceProperties.getUrl());
            apiClient.setBasePath(serviceProperties.getUrl());
        }
        else {
            log.warn("RBAC service URL not set...");
        }

        return new RbacApiImpl(apiClient);
    }

    /**
     * Customize the creation of the HTTP client the API will be using.
     *
     * @param client the associated ApiClient instance
     * @return a new Http client instance.
     */
    private Client buildHttpClient(ApiClient client) {
        HttpClientBuilder apacheBuilder = HttpClientBuilder.create();

        // Bump the max connections so that we don't block on multiple async requests
        // to the RBAC service.
        apacheBuilder.setMaxConnPerRoute(Integer.MAX_VALUE);
        apacheBuilder.setMaxConnTotal(Integer.MAX_VALUE);
        HttpClient httpClient = apacheBuilder.build();

        ClientHttpEngine engine = ApacheHttpClient4EngineFactory.create(httpClient);

        ClientConfiguration clientConfig = new ClientConfiguration(ResteasyProviderFactory.getInstance());
        clientConfig.register(client.getJSON());
        if (client.isDebugging()) {
            clientConfig.register(org.jboss.logging.Logger.class);
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

        return ((ResteasyClientBuilder) clientBuilder).httpEngine(engine).build();
    }

    @Override
    public Class<?> getObjectType() {
        return RbacApi.class;
    }

}
