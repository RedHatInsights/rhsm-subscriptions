package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;

import java.util.Collections;
import java.util.List;

public class StubSearchApi extends SearchApi {

    @Override
    public List<Subscription> searchSubscriptions(String criteria, String options) throws ApiException{
        return Collections.singletonList(createData());
    }

    private Subscription createData() {
        return new Subscription().subscriptionNumber("2253591");
    }
}
