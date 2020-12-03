package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.subscription.api.resources.SearchApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("api,test")
public class SubscriptionServiceTest {

    @MockBean
    SearchApi searchApi;

    @Autowired
    SubscriptionService subject;

    @Test
    public void verifyCallIsMadeCorrectlyTest() throws ApiException {
        when(searchApi
                .searchSubscriptions("criteria;web_customer_id=123;statusList=active;statusList=temporary",
                        "options;products=ALL")).thenReturn(Collections.emptyList());
        subject.getSubscriptions("123");
        verify(searchApi, only())
                .searchSubscriptions("criteria;web_customer_id=123;statusList=active;statusList=temporary",
                        "options;products=ALL");
    }
}
