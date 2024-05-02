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
package com.redhat.swatch.contract.resource;

import static com.redhat.swatch.contract.resource.ContractsTestingResource.FEATURE_NOT_ENABLED_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.service.EnabledOrgsProducer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(
    user = "placeholder",
    roles = {"service"})
class ContractsTestingResourceTest {
  @InjectMock ApplicationConfiguration applicationConfiguration;
  @InjectMock EnabledOrgsProducer enabledOrgsProducer;
  @Inject ContractsTestingResource resource;

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsNotEnabled() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(false);
    var result = resource.syncAllSubscriptions(false);
    assertEquals(FEATURE_NOT_ENABLED_MESSAGE, result.getResult());
    verify(enabledOrgsProducer, times(0)).sendTaskForSubscriptionsSync();
  }

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsNotEnabledButForce() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(false);
    var result = resource.syncAllSubscriptions(true);
    assertNull(result.getResult());
    verify(enabledOrgsProducer).sendTaskForSubscriptionsSync();
  }

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsEnabled() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(true);
    var result = resource.syncAllSubscriptions(false);
    assertNull(result.getResult());
    verify(enabledOrgsProducer).sendTaskForSubscriptionsSync();
  }

  @Test
  void testPruneUnlistedSubscriptions() {
    var result = resource.pruneUnlistedSubscriptions();
    assertNull(result.getResult());
    verify(enabledOrgsProducer).sendTaskForSubscriptionsPrune();
  }
}
