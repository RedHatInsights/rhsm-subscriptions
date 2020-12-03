package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.subscription.ApiException;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionReportSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithMockRedHatPrincipal("123456")
@ActiveProfiles("api,test")
public class SubscriptionsResourceTest {

    @MockBean
    SubscriptionCapacityRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    SubscriptionService subscriptionService;

    @MockBean
    AccountListSource accountListSource;

    @Autowired
    SubscriptionsResource subject;

    static final Sort.Order IMPLICIT_ORDER = new Sort.Order(Sort.Direction.ASC, "id");

    @BeforeEach
    public void setup() throws ApiException, AccountListSourceException {
        PageImpl<SubscriptionView> mockPage = new PageImpl<>(Collections.emptyList());
        when(repository.getSubscriptionViews(any(), any(), any(), any(), any()))
                .thenReturn(mockPage);
        when(subscriptionService.getSubscriptions("owner123456")).thenReturn(Collections.emptyList());
        when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
    }

    @Test
    public void shouldInvokeRepositoryAndService() throws ApiException {
        subject.getSubscriptions("RHEL", 0, 1, null, null, null,
                SubscriptionReportSort.PHYSICAL_CAPACITY, SortDirection.ASC);
        verify(repository, only())
                .getSubscriptionViews(
                        eq("account123456"),
                        eq("RHEL"),
                        eq(ServiceLevel.ANY),
                        eq(Usage.ANY),
                        eq(PageRequest.of(0, 1,
                                Sort.by(Sort.Order.asc(
                                        SubscriptionsResource.SORT_PARAM_MAPPING.get(
                                                SubscriptionReportSort.PHYSICAL_CAPACITY)),
                                        IMPLICIT_ORDER)
                )));
        verify(subscriptionService, only())
                .getSubscriptions("owner123456");
    }
}
