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
package org.candlepin.subscriptions.rhmarketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles({"rh-marketplace", "test"})
class RhMarketplaceSubscriptionIdProviderTest {

  @MockBean private SubscriptionRepository repo;

  @MockBean private SubscriptionSyncController syncController;

  @MockBean private RhMarketplaceSubscriptionCollector collector;

  @MockBean private TagProfile mockProfile;

  @Autowired private RhMarketplaceSubscriptionIdProvider idProvider;

  private OffsetDateTime rangeStart = OffsetDateTime.now().minusDays(5);
  private OffsetDateTime rangeEnd = OffsetDateTime.now().plusDays(5);

  @Test
  void doesNotAllowReservedValuesInKey() {
    UsageCalculation.Key key1 = new Key(String.valueOf(1), ServiceLevel._ANY, Usage.PRODUCTION);
    UsageCalculation.Key key2 = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage._ANY);

    assertThrows(
        IllegalArgumentException.class,
        () -> idProvider.findSubscriptionId("1000", "org1000", key1, rangeStart, rangeEnd));
    assertThrows(
        IllegalArgumentException.class,
        () -> idProvider.findSubscriptionId("1000", "org1000", key2, rangeStart, rangeEnd));
  }

  @Test
  void findsSubscriptionId() {
    UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProviderId("xyz");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(any())).thenReturn(productNames);
    when(repo.findByAccountAndProductNameAndServiceLevel(
            eq("1000"),
            eq(key),
            eq(productNames),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    Optional<String> actual =
        idProvider.findSubscriptionId("1000", "org1000", key, rangeStart, rangeEnd);
    assertEquals("xyz", actual.get());
  }

  @Test
  void memoizesSubscriptionId() {
    UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
    Subscription s = new Subscription();
    s.setStartDate(OffsetDateTime.now().minusDays(7));
    s.setEndDate(OffsetDateTime.now().plusDays(7));
    s.setBillingProviderId("abc");
    List<Subscription> result = Collections.singletonList(s);

    Set<String> productNames = Set.of("OpenShift Container Platform");
    when(mockProfile.getOfferingProductNamesForTag(anyString())).thenReturn(productNames);
    when(repo.findByAccountAndProductNameAndServiceLevel(
            eq("1000"),
            eq(key),
            eq(productNames),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(new ArrayList<>())
        .thenReturn(result);

    Optional<String> actual =
        idProvider.findSubscriptionId("1000", "org1000", key, rangeStart, rangeEnd);
    assertEquals("abc", actual.get());

    verify(collector).requestSubscriptions("org1000");
  }
}
