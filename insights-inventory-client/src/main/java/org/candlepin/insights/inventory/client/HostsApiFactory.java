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
package org.candlepin.insights.inventory.client;

import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * Factory that produces inventory service clients using configuration.
 */
public class HostsApiFactory implements FactoryBean<HostsApi> {

    private static Logger log = LoggerFactory.getLogger(HostsApiFactory.class);

    private final InventoryServiceProperties serviceProperties;

    public HostsApiFactory(InventoryServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    public HostsApi getObject() throws Exception {
        if (serviceProperties.isUseStub()) {
            log.info("Using stub host inventory client");
            return new StubHostsApi();
        }
        ApiClient apiClient = new ApiClient();
        if (serviceProperties.getUrl() != null) {
            log.info("Host inventory service URL: {}", serviceProperties.getUrl());
            apiClient.setBasePath(serviceProperties.getUrl());
        }
        else {
            log.warn("Host inventory service URL not set...");
        }

        return new HostsApi(apiClient);
    }

    @Override
    public Class<?> getObjectType() {
        return HostsApi.class;
    }
}
