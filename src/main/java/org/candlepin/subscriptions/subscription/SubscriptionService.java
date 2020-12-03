package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubscriptionService {

    private final SearchApi searchApi;

    public SubscriptionService(SearchApi searchApi) {
        this.searchApi = searchApi;
    }

    public List<Subscription> getSubscriptions(String orgId) throws ApiException {
        return searchApi.searchSubscriptions(
                String.format("criteria;web_customer_id=%s;statusList=active;statusList=temporary", orgId),
                "options;products=ALL"
        );
    }
}
