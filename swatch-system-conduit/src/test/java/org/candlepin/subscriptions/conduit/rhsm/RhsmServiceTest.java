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
package org.candlepin.subscriptions.conduit.rhsm;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Collections;
import javax.validation.ConstraintViolationException;
import org.candlepin.subscriptions.conduit.inventory.InventoryServiceProperties;
import org.candlepin.subscriptions.conduit.rhsm.client.ApiException;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory;
import org.candlepin.subscriptions.conduit.rhsm.client.resources.RhsmApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"rhsm-conduit", "test", "kafka-queue"})
class RhsmServiceTest {
  @Autowired
  @Qualifier("rhsmRetryTemplate")
  private RetryTemplate retryTemplate;

  @Autowired private RhsmService rhsmService;

  @Autowired private InventoryServiceProperties inventoryServiceProperties;

  @MockBean private RhsmApi rhsmApi;

  /*
  @TestConfiguration
  static class MockedInventoryServiceConfiguration {
      @Bean
      @Primary
      public InventoryServiceProperties testingInventoryServiceProperties() {
          InventoryServiceProperties inventoryServiceProperties = new InventoryServiceProperties();
          inventoryServiceProperties.setApiKey("changeit");
          return inventoryServiceProperties;
      }
  }
   */

  @Test
  void testRhsmServiceRetry() throws Exception {
    when(rhsmApi.getConsumersForOrg(
            anyString(), any(Integer.class), nullable(String.class), anyString()))
        .thenThrow(ApiException.class);

    // Make the tests run faster!
    retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
    RhsmService mockBackedService =
        new RhsmService(
            inventoryServiceProperties, new RhsmApiProperties(), rhsmApi, retryTemplate);

    assertThrows(
        ApiException.class,
        () -> mockBackedService.getPageOfConsumers("123", null, mockBackedService.formattedTime()));

    verify(rhsmApi, times(10))
        .getConsumersForOrg(anyString(), any(Integer.class), nullable(String.class), anyString());
  }

  @Test
  void testRhsmServiceLastCheckInValidationBad() throws Exception {
    assertThrows(
        ConstraintViolationException.class,
        () -> rhsmService.getPageOfConsumers("", "", "Does Not Validate"));
  }

  @Test
  void testRhsmServiceLastCheckInValidationBadWithNanos() throws Exception {
    String time = "2020-01-01T13:00:00.725Z";
    assertThrows(
        ConstraintViolationException.class,
        () -> rhsmService.getPageOfConsumers("org", "offset", time));
  }

  @Test
  void testRhsmServiceLastCheckInValidationGood() throws Exception {
    String time = "2020-01-01T13:00:00Z";
    OrgInventory expected = new OrgInventory().body(Collections.emptyList());
    when(rhsmApi.getConsumersForOrg(eq("org"), anyInt(), eq("offset"), eq(time)))
        .thenReturn(expected);

    OrgInventory actual = rhsmService.getPageOfConsumers("org", "offset", time);
    assertSame(expected, actual);
  }
}
