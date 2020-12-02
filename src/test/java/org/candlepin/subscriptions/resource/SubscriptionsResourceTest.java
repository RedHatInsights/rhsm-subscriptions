package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.subscription.ApiException;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
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
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
@WithMockRedHatPrincipal("123456")
public class SubscriptionsResourceTest {

    @MockBean
    SubscriptionCapacityRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    SubscriptionService subscriptionService;

    @Autowired
    SubscriptionsResource subject;

    static final Sort.Order IMPLICIT_ORDER = new Sort.Order(Sort.Direction.ASC, "id");

    @BeforeEach
    public void setup() throws ApiException {
        PageImpl<SubscriptionView> mockPage = new PageImpl<>(Collections.emptyList());
        when(repository.getSubscriptionViews(any(), any(), any(), any(), any()))
                .thenReturn(mockPage);
        when(subscriptionService.getSubscriptions(123456)).thenReturn(Collections.emptyList());
    }

    @Test
    public void shouldInvokeRepositoryAndService() {
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
    }
}
