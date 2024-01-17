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
package org.candlepin.subscriptions.capacity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageContextSubscriptionProviderTest {

  private static final String MISSING_SUBSCRIPTIONS_COUNTER_NAME = "missing_subscriptions";
  private static final String AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME = "ambiguous_subscriptions";

  @Mock private SubscriptionSyncController syncController;
  private MeterRegistry meterRegistry;
  private UsageContextSubscriptionProvider provider;
  private OffsetDateTime defaultEndDate =
      OffsetDateTime.of(2022, 7, 22, 8, 0, 0, 0, ZoneOffset.UTC);
  private OffsetDateTime defaultLookUpDate =
      OffsetDateTime.of(2022, 6, 22, 8, 0, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setupTest() {
    meterRegistry = new SimpleMeterRegistry();
    provider =
        new UsageContextSubscriptionProvider(
            syncController,
            meterRegistry.counter(MISSING_SUBSCRIPTIONS_COUNTER_NAME),
            meterRegistry.counter(AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME),
            BillingProvider.AWS);
  }

  @Test
  void incrementsMissingCounter_WhenOrgIdPresent() {
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            provider.getSubscription(
                "org123", "rosa", "Premium", "Production", "123", defaultLookUpDate));
    Counter counter = meterRegistry.counter(MISSING_SUBSCRIPTIONS_COUNTER_NAME);
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsAmbiguousCounter_WhenOrgIdPresent() {
    Subscription sub1 = new Subscription();
    sub1.setSubscriptionId("SUB1");
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(defaultEndDate);
    Subscription sub2 = new Subscription();
    sub2.setSubscriptionId("SUB2");
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(defaultEndDate);

    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    Optional<Subscription> subscription =
        provider.getSubscription(
            "org123", "rosa", "Premium", "Production", "123", defaultLookUpDate);
    assertTrue(subscription.isPresent());
    assertEquals("SUB1", subscription.get().getSubscriptionId());

    Counter counter = meterRegistry.counter(AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME);
    assertEquals(1.0, counter.count());
  }

  @Test
  void shouldThrowSubscriptionsExceptionForTerminatedSubscription_WhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(endDate);
    when(syncController.findSubscriptions(any(), any(), any(), any())).thenReturn(List.of(sub1));

    var lookupDate = endDate.plusMinutes(30);
    var exception =
        assertThrows(
            SubscriptionsException.class,
            () -> {
              provider.getSubscription(
                  "org123", "rosa", "Premium", "Production", "123", lookupDate);
            });

    assertEquals(
        ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED.getDescription(),
        exception.getCode().getDescription());
  }

  @Test
  void shouldReturnActiveSubscriptionAndNotTerminated_WhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(endDate);
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(endDate.plusMinutes(45));

    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    var lookupDate = endDate.plusMinutes(30);
    Optional<Subscription> subscription =
        provider.getSubscription("org123", "rosa", "Premium", "Production", "123", lookupDate);
    assertTrue(subscription.isPresent());
    assertEquals(sub2, subscription.get());
  }

  @Test
  void testShouldReturnSubscriptionWithPartialMatchingBillingAccountId() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    Subscription sub1 = new Subscription();
    sub1.setBillingAccountId("marketplaceTenantId1;marketplaceSubscriptionId1");
    sub1.setEndDate(endDate);
    Subscription sub2 = new Subscription();
    sub2.setBillingAccountId("marketplaceTenantId1;marketplaceSubscriptionId2");
    sub2.setEndDate(endDate.plusMinutes(45));
    Subscription sub3 = new Subscription();
    sub3.setBillingAccountId("marketplaceTenantId1");
    sub3.setEndDate(endDate.plusMinutes(60));

    Key usageKey =
        new UsageCalculation.Key(
            "rosa",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "marketplaceTenantId1");

    var lookupDate = endDate.plusMinutes(30);

    when(syncController.findSubscriptions(
            Optional.of("org123"), usageKey, lookupDate.minusHours(1), lookupDate))
        .thenReturn(List.of(sub1, sub2, sub3));

    Optional<Subscription> subscription =
        provider.getSubscription(
            "org123", "rosa", "Premium", "Production", "marketplaceTenantId1", lookupDate);
    assertEquals(sub2, subscription.get());
  }
}
