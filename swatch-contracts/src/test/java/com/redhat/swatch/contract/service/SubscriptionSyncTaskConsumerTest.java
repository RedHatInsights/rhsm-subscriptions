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

import static org.mockito.Mockito.verify;

import com.redhat.swatch.contract.model.EnabledOrgsResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionSyncTaskConsumerTest {

  private static final String ORG_ID = "org123";

  @InjectMock SubscriptionSyncService service;
  @Inject SubscriptionSyncTaskConsumer consumer;

  @Test
  void testConsumeFromTopic() {
    consumer.consumeFromTopic(new EnabledOrgsResponse().withOrgId(ORG_ID));

    // Then the subscription should be synced.
    verify(service).reconcileSubscriptionsWithSubscriptionService(ORG_ID, false);
  }
}
