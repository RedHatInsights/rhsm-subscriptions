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
package org.candlepin.subscriptions.subscription;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@DirtiesContext
@ActiveProfiles({"capacity-ingress", "test"})
class SubscriptionSyncControllerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final String SKU = "testsku";
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";

  @Autowired SubscriptionSyncController subscriptionSyncController;

  @MockBean ProductDenylist denylist;

  @MockBean OfferingRepository offeringRepository;

  @MockBean SubscriptionRepository subscriptionRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @MockBean SubscriptionService subscriptionService;

  private OffsetDateTime rangeStart = OffsetDateTime.now().minusDays(5);
  private OffsetDateTime rangeEnd = OffsetDateTime.now().plusDays(5);

  @BeforeEach
  void setUp() {
    var offering = Offering.builder().sku(SKU).productIds(new HashSet<>(List.of(68))).build();
    Mockito.when(offeringRepository.getReferenceById(SKU)).thenReturn(offering);
  }

  @Test
  void doesNotAllowReservedValuesInKey() {
    UsageCalculation.Key key1 =
        new Key(
            String.valueOf(1),
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            BILLING_ACCOUNT_ID_ANY);
    UsageCalculation.Key key2 =
        new Key(
            String.valueOf(1),
            ServiceLevel.STANDARD,
            Usage._ANY,
            BillingProvider._ANY,
            BILLING_ACCOUNT_ID_ANY);
    var orgId = "org1000";

    assertThrows(
        IllegalArgumentException.class,
        () -> subscriptionSyncController.findSubscriptions(orgId, key1, rangeStart, rangeEnd));
    assertThrows(
        IllegalArgumentException.class,
        () -> subscriptionSyncController.findSubscriptions(orgId, key2, rangeStart, rangeEnd));
  }

  @Test
  void findsSubscriptionId_WhenOrgIdPresent() {
    UsageCalculation.Key key =
        new Key(
            "OpenShift-metrics",
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = createSubscription("org123", "sku", "foo", "890");
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(result);

    List<Subscription> actual =
        subscriptionSyncController.findSubscriptions("org1000", key, rangeStart, rangeEnd);
    assertEquals(1, actual.size());
    assertEquals("xyz", actual.get(0).getBillingProviderId());
  }

  @Test
  void invalidProductTagIfNoMatchWithConfig() {
    UsageCalculation.Key key =
        new Key(
            "openshift-container-platform",
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "xyz");
    Subscription s = createSubscription("org123", "sku", "foo", "890");
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProvider(BillingProvider.RED_HAT);
    s.setBillingProviderId("xyz");
    List<Subscription> actual =
        subscriptionSyncController.findSubscriptions("org1000", key, rangeStart, rangeEnd);
    assertEquals(0, actual.size());
  }

  @Test
  void terminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    Offering o = new Offering();
    o.setMetered(true);
    when(offeringRepository.findById(SKU)).thenReturn(Optional.of(o));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void lateTerminateActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    Offering offering = Offering.builder().metered(true).build();
    s.setOffering(offering);
    when(offeringRepository.findById(SKU)).thenReturn(Optional.of(offering));
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now().minusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateInTheFutureActivePAYGSubscriptionTest() {
    Subscription s = createSubscription();
    s.getOffering().setMetered(true);
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now().plusDays(1);
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(
        result,
        matchesPattern("Subscription 456 terminated at .* with out of range termination date .*"));
    assertEquals(termination, s.getEndDate());
  }

  @Test
  void terminateActiveNonPAYGSubscriptionTest() {
    Subscription s = createSubscription();
    when(subscriptionRepository.findActiveSubscription("456")).thenReturn(List.of(s));

    var termination = OffsetDateTime.now();
    var result = subscriptionSyncController.terminateSubscription("456", termination);
    assertThat(result, matchesPattern("Subscription 456 terminated at .*\\."));
    assertEquals(termination, s.getEndDate());
  }

  private Subscription createSubscription() {
    return createSubscription("123", SKU, "456", "890");
  }

  private Subscription createSubscription(String orgId, String sku, String subId, String subNum) {
    Offering offering = Offering.builder().sku(sku).build();

    return Subscription.builder()
        .subscriptionId(subId)
        .subscriptionNumber(subNum)
        .orgId(orgId)
        .quantity(4L)
        .offering(offering)
        .startDate(NOW)
        .endDate(NOW.plusDays(30))
        .build();
  }
}
