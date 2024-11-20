/*
 * Copyright Red Hat, Inc.
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
package com.redhat.swatch.contract.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.DbReportCriteria;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageContextSubscriptionProviderTest {

  private static final String MISSING_SUBSCRIPTIONS_COUNTER_NAME = "swatch_missing_subscriptions";
  private static final String AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME =
      "swatch_ambiguous_subscriptions";
  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.of(2022, 7, 22, 8, 0, 0, 0, ZoneOffset.UTC);

  @Mock private SubscriptionRepository repo;
  private MeterRegistry meterRegistry;
  private UsageContextSubscriptionProvider provider;
  private DbReportCriteria criteria;

  @BeforeEach
  void setupTest() {
    givenDefaultCriteria();
    meterRegistry = new SimpleMeterRegistry();
    provider = new UsageContextSubscriptionProvider(repo, meterRegistry);
  }

  @Test
  void incrementsMissingCounter_WhenOrgIdPresent() {
    when(repo.findByCriteria(any(), any())).thenReturn(Collections.emptyList());
    assertThrows(NotFoundException.class, this::whenGetSubscriptions);
    Counter counter = meterRegistry.counter(MISSING_SUBSCRIPTIONS_COUNTER_NAME, "provider", "aws");
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsAmbiguousCounter_WhenOrgIdPresent() {
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setSubscriptionId("SUB1");
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setBillingAccountId("123");
    sub1.setEndDate(DEFAULT_END_DATE);
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setSubscriptionId("SUB2");
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setBillingAccountId("123");
    sub2.setEndDate(DEFAULT_END_DATE);

    when(repo.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));

    Optional<SubscriptionEntity> subscription = whenGetSubscriptions();
    assertTrue(subscription.isPresent());
    assertEquals("SUB1", subscription.get().getSubscriptionId());

    Counter counter =
        meterRegistry.counter(AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME, "provider", "aws");
    assertEquals(1.0, counter.count());
  }

  @Test
  void shouldThrowSubscriptionsExceptionForTerminatedSubscription_WhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setBillingAccountId("123");
    sub1.setEndDate(endDate);
    when(repo.findByCriteria(any(), any())).thenReturn(List.of(sub1));

    givenCriteriaWithEnding(endDate.plusMinutes(30));
    var exception = assertThrows(ServiceException.class, this::whenGetSubscriptions);

    assertEquals(
        ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED.getDescription(),
        exception.getCode().getDescription());
  }

  @Test
  void shouldReturnActiveSubscriptionAndNotTerminated_WhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setBillingAccountId("123");
    sub1.setEndDate(endDate);
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setBillingAccountId("123");
    sub2.setEndDate(endDate.plusMinutes(45));

    when(repo.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));

    givenCriteriaWithEnding(endDate.plusMinutes(30));
    Optional<SubscriptionEntity> subscription = whenGetSubscriptions();
    assertTrue(subscription.isPresent());
    assertEquals(sub2, subscription.get());
  }

  private void givenDefaultCriteria() {
    givenCriteriaWithEnding(DEFAULT_END_DATE);
  }

  private void givenCriteriaWithEnding(OffsetDateTime ending) {
    this.criteria =
        DbReportCriteria.builder()
            .orgId("org123")
            .productTag("rosa")
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage.PRODUCTION)
            .billingProvider(BillingProvider.AWS)
            .billingAccountId("123")
            .beginning(ending.minusHours(1))
            .ending(ending)
            .build();
  }

  private Optional<SubscriptionEntity> whenGetSubscriptions() {
    return provider.getSubscription(criteria);
  }
}
