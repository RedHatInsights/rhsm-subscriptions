package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.http.HttpClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.candlepin.subscriptions.subscription")
public class SubscriptionServiceConfiguration {

    @Bean
    @Qualifier("subscription")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.subscription")
    public HttpClientProperties subscriptionServiceProperties() {
        return new HttpClientProperties();
    }

    @Bean
    public SearchApiFactory searchApiFactory(@Qualifier("subscription") HttpClientProperties props) {
        return new SearchApiFactory(props);
    }
}
