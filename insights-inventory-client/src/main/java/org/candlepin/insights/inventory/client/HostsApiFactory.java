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

    private final InventoryServiceConfiguration config;

    public HostsApiFactory(InventoryServiceConfiguration config) {
        this.config = config;
    }

    @Override
    public HostsApi getObject() throws Exception {
        if (config.isUseStub()) {
            log.info("Using stub host inventory client");
            return new StubHostsApi();
        }
        ApiClient apiClient = new ApiClient();
        if (config.getUrl() != null) {
            log.info("Host inventory service URL: {}", config.getUrl());
            apiClient.setBasePath(config.getUrl());
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
