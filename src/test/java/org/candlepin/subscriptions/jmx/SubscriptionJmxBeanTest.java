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
package org.candlepin.subscriptions.jmx;

import java.util.stream.IntStream;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.jmx.capacity.SubscriptionJmxBean;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionJmxBeanTest {

  @Mock private SubscriptionSyncController subscriptionSyncController;

  @Mock private OrgConfigRepository orgConfigRepository;

  private SubscriptionJmxBean subject;

  @BeforeEach
  void setup() {
    subject = new SubscriptionJmxBean(subscriptionSyncController, orgConfigRepository);
  }

  @Test
  void syncAllSubscriptionsTest() {
    Mockito.when(orgConfigRepository.findSyncEnabledOrgs())
        .thenReturn(IntStream.range(1, 10).mapToObj(String::valueOf));
    subject.syncAllSubscriptions();
    Mockito.verify(subscriptionSyncController, Mockito.times(9))
        .syncAllSubcriptionsForOrg(Mockito.anyString());
  }

  @Test
  void syncSubscriptionForOrgTest() {
    subject.syncSubscriptionsForOrg("123");
    Mockito.verify(subscriptionSyncController).syncAllSubcriptionsForOrg("123");
  }

  @Test
  void syncSubscriptionTest() {
    subject.syncSubscription("testid");
    Mockito.verify(subscriptionSyncController).syncSubscription("testid");
  }
}
