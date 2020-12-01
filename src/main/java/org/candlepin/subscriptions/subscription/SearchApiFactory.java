package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.http.HttpClient;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

public class SearchApiFactory implements FactoryBean<SearchApi> {
    private static final Logger log = LoggerFactory.getLogger(SearchApiFactory.class);

    private final HttpClientProperties serviceProperties;

    public SearchApiFactory(HttpClientProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }

    @Override
    public SearchApi getObject() throws Exception {
        if (serviceProperties.isUseStub()) {
            log.info("Using stub subscription client");
            return new StubSearchApi();
        }
        final ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setHttpClient(HttpClient.buildHttpClient(serviceProperties, apiClient.getJSON(),
                apiClient.isDebugging()));
        if (serviceProperties.getUrl() != null) {
            log.info("Subscription service URL: {}", serviceProperties.getUrl());
            apiClient.setBasePath(serviceProperties.getUrl());
        }
        else {
            log.warn("Subscription service URL not set...");
        }
        return new SearchApi(apiClient);
    }

    @Override
    public Class<?> getObjectType() {
        return SearchApi.class;
    }
}
