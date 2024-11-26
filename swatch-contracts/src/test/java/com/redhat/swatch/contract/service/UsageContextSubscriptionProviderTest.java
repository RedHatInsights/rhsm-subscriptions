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

import static com.redhat.swatch.contract.service.UsageContextSubscriptionProvider.AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME;
import static com.redhat.swatch.contract.service.UsageContextSubscriptionProvider.MISSING_SUBSCRIPTIONS_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.DbReportCriteria;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UsageContextSubscriptionProviderTest {

  private static final String ORG_ID = "org123";
  private static final String BILLING_ACCOUNT_ID = "123";
  private static final Usage USAGE = Usage.PRODUCTION;
  private static final ServiceLevel SLA = ServiceLevel.PREMIUM;
  private static final String PRODUCT = "rosa";
  private static final BillingProvider BILLING_PROVIDER = BillingProvider.AWS;
  private static final OffsetDateTime DEFAULT_END_DATE =
      OffsetDateTime.of(2022, 7, 22, 8, 0, 0, 0, ZoneOffset.UTC);

  @Inject SubscriptionRepository repo;
  @Inject OfferingRepository offeringRepository;
  @Inject MeterRegistry meterRegistry;
  @Inject UsageContextSubscriptionProvider provider;
  private DbReportCriteria criteria;
  private OfferingEntity offering;

  @Transactional
  @BeforeEach
  void setupTest() {
    givenDefaultCriteria();
    meterRegistry.clear();
    repo.deleteAll();
    offeringRepository.deleteAll();
    givenExistingOffering();
  }

  @Test
  void incrementsMissingCounterWhenOrgIdPresent() {
    assertThrows(NotFoundException.class, this::whenGetSubscriptions);
    thenCounterIs(MISSING_SUBSCRIPTIONS_COUNTER_NAME, 1.0);
  }

  @Test
  void incrementsAmbiguousCounterWhenOrgIdPresent() {
    var sub1 = givenNewSubscription();
    givenNewSubscription();
    Optional<SubscriptionEntity> subscription = whenGetSubscriptions();
    assertTrue(subscription.isPresent());
    assertEquals(sub1.getSubscriptionId(), subscription.get().getSubscriptionId());
    thenCounterIs(AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME, 1.0);
  }

  @Test
  void shouldThrowSubscriptionsExceptionForTerminatedSubscriptionWhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    givenNewSubscriptionWithEndDate(endDate);

    givenCriteriaWithEnding(endDate.plusMinutes(30));
    var exception = assertThrows(ServiceException.class, this::whenGetSubscriptions);

    assertEquals(
        ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED.getDescription(),
        exception.getCode().getDescription());
  }

  @Test
  void shouldReturnActiveSubscriptionAndNotTerminatedWhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    givenNewSubscriptionWithEndDate(endDate);
    var sub2 = givenNewSubscriptionWithEndDate(endDate.plusMinutes(30));

    givenCriteriaWithEnding(endDate.plusMinutes(30));
    Optional<SubscriptionEntity> subscription = whenGetSubscriptions();
    assertTrue(subscription.isPresent());
    assertEquals(sub2.getSubscriptionId(), subscription.get().getSubscriptionId());
  }

  SubscriptionEntity givenNewSubscription() {
    return givenNewSubscription(DEFAULT_END_DATE);
  }

  SubscriptionEntity givenNewSubscriptionWithEndDate(OffsetDateTime endDate) {
    return givenNewSubscription(endDate);
  }

  @Transactional
  SubscriptionEntity givenNewSubscription(OffsetDateTime endDate) {
    SubscriptionEntity sub = new SubscriptionEntity();
    sub.setSubscriptionId(UUID.randomUUID().toString());
    sub.setBillingProvider(BILLING_PROVIDER);
    sub.setBillingProviderId(UUID.randomUUID().toString());
    sub.setBillingAccountId(BILLING_ACCOUNT_ID);
    sub.setStartDate(endDate.minusDays(10));
    sub.setEndDate(endDate);
    sub.setOrgId(ORG_ID);
    sub.setOffering(offering);
    repo.persist(sub);
    return sub;
  }

  private void givenExistingOffering() {
    offering = new OfferingEntity();
    offering.setSku(UUID.randomUUID().toString());
    offering.setUsage(USAGE);
    offering.setServiceLevel(SLA);
    offering.setProductTags(Set.of(PRODUCT));
    offeringRepository.persist(offering);
  }

  private void givenDefaultCriteria() {
    givenCriteriaWithEnding(DEFAULT_END_DATE);
  }

  private void givenCriteriaWithEnding(OffsetDateTime ending) {
    this.criteria =
        DbReportCriteria.builder()
            .orgId(ORG_ID)
            .productTag(PRODUCT)
            .serviceLevel(SLA)
            .usage(USAGE)
            .billingProvider(BILLING_PROVIDER)
            .billingAccountId(BILLING_ACCOUNT_ID)
            .beginning(ending.minusHours(1))
            .ending(ending)
            .build();
  }

  private Optional<SubscriptionEntity> whenGetSubscriptions() {
    return provider.getSubscription(criteria);
  }

  private void thenCounterIs(String counterName, double expected) {
    Counter counter =
        meterRegistry.counter(
            counterName, "provider", BILLING_PROVIDER.getValue(), "product", PRODUCT);
    assertEquals(expected, counter.count());
  }
}
