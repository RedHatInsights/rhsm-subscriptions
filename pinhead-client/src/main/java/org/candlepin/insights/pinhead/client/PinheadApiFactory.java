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

import org.candlepin.insights.pinhead.client.resources.PinheadApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * Builds a PinheadApi, which may be a stub, or a normal client, with or without cert auth depending on
 * properties.
 */
public class PinheadApiFactory implements FactoryBean<PinheadApi> {
    private static Logger log = LoggerFactory.getLogger(PinheadApiFactory.class);

    private final PinheadApiProperties properties;

    public PinheadApiFactory(PinheadApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public PinheadApi getObject() throws Exception {
        if (properties.isUseStub()) {
            log.info("Using stub pinhead client");
            return new StubPinheadApi();
        }

        ApiClient client;
        if (properties.usesClientAuth()) {
            log.info("Pinhead client configured with client-cert auth");
            client = new X509ApiClientFactory(properties.getX509ApiClientFactoryConfiguration()).getObject();
        }
        else {
            log.info("Pinhead client configured without client-cert auth");
            client = new ApiClient();
        }
        if (properties.getUrl() != null) {
            log.info("Pinhead URL: {}", properties.getUrl());
            client.setBasePath(properties.getUrl());
            client.addDefaultHeader("cp-lookup-permissions", "false");
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
}
