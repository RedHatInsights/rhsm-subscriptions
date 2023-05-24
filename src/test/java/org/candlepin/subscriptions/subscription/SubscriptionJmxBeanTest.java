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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.umb.UmbProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jmx.JmxException;

@ExtendWith(MockitoExtension.class)
class SubscriptionJmxBeanTest {

  @Mock private SubscriptionSyncController subscriptionSyncController;
  @Mock private SubscriptionPruneController subscriptionPruneController;
  @Mock private JmsTemplate mockJmsTemplate;

  private SubscriptionJmxBean subject;
  private SecurityProperties properties;

  @BeforeEach
  void setup() {
    properties = new SecurityProperties();
    subject =
        new SubscriptionJmxBean(
            subscriptionSyncController,
            subscriptionPruneController,
            properties,
            new UmbProperties(),
            mockJmsTemplate);
  }

  @Test
  void syncAllSubscriptionsTest() {
    subject.syncAllSubscriptions();
    verify(subscriptionSyncController).syncAllSubscriptionsForAllOrgs();
  }

  @Test
  void saveSubscriptionsNotEnabled() {
    assertThrows(JmxException.class, () -> subject.saveSubscriptions("[]", false));
  }

  @Test
  void saveSubscriptionsDevMode() {
    properties.setDevMode(true);
    subject.saveSubscriptions("foo", true);
    verify(subscriptionSyncController).saveSubscriptions("foo", true);
  }

  @Test
  void saveSubscriptionsManualSubsEditingEnabled() {
    properties.setManualSubscriptionEditingEnabled(true);
    subject.saveSubscriptions("foo", true);
    verify(subscriptionSyncController).saveSubscriptions("foo", true);
  }

  @Test
  void pruneUnlistedSubscriptionsTest() {
    subject.pruneUnlistedSubscriptions();
    verify(subscriptionPruneController).pruneAllUnlistedSubscriptions();
  }

  @Test
  void syncSubscriptionForOrgTest() {
    subject.syncSubscriptionsForOrg("123");
    verify(subscriptionSyncController).reconcileSubscriptionsWithSubscriptionService("123");
  }

  @Test
  void syncSubscriptionTest() {
    subject.syncSubscription("testid");
    verify(subscriptionSyncController).syncSubscription("testid");
  }
}
