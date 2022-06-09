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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilterModifyingConfigurer;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.security.WithMockPskPrincipal;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@WebAppConfiguration
@ActiveProfiles({"capacity-ingress", "test"})
class InternalSubscriptionResourceTest {
  @MockBean SubscriptionSyncController syncController;
  @Autowired SecurityProperties properties;
  @Autowired WebApplicationContext context;
  @Autowired InternalSubscriptionResource resource;

  private MockMvc mvc;
  private static final String SYNC_ORG_123 = "/internal/subscriptions/sync/org/123";

  @BeforeEach
  public void setup() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(new IdentityHeaderAuthenticationFilterModifyingConfigurer())
            .build();
  }

  @Test
  void forceSyncForOrgShouldReturnSuccess() {
    assertEquals("Sync started.", resource.forceSyncSubscriptionsForOrg("123"));
  }

  @Test
  void incrementsMissingCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(meterRegistry, syncController, properties);
    when(syncController.findSubscriptionsAndSyncIfNeeded(any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            resource.getAwsUsageContext(
                "account123", OffsetDateTime.MIN, "rhosak", "Premium", "Production", "123"));
    Counter counter = meterRegistry.counter("swatch_missing_aws_subscription");
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsAmbiguousCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(meterRegistry, syncController, properties);
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    when(syncController.findSubscriptionsAndSyncIfNeeded(any(), any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));
    AwsUsageContext awsUsageContext =
        resource.getAwsUsageContext(
            "account123", OffsetDateTime.MIN, "rhosak", "Premium", "Production", "123");
    Counter counter = meterRegistry.counter("swatch_ambiguous_aws_subscription");
    assertEquals(1.0, counter.count());
    assertEquals("foo1", awsUsageContext.getProductCode());
    assertEquals("foo2", awsUsageContext.getCustomerId());
    assertEquals("foo3", awsUsageContext.getAwsSellerAccountId());
  }

  @Test
  @WithMockPskPrincipal
  void forceSyncForOrgWorksWithPsk() throws Exception {
    /* Why does this test expect isNotFound()?  Because we are using JAX-RS for our request
     * mapping. MockMvc only works with Spring's custom RestController standard, but it's really
     * handy to use for setting up the Spring Security filter chain.  It's a dirty hack, but we
     * can use MockMvc to test authentication and authorization by looking for a 403 response and
     * if we get a 404 response, it means everything passed security-wise and we just couldn't
     * find the matching resource (because there are no matching RestControllers!).
     */
    mvc.perform(post(SYNC_ORG_123)).andExpect(status().isNotFound());
  }

  @Test
  void forceSyncForOrgWorksFailsWithNoPrincipal() throws Exception {
    mvc.perform(post(SYNC_ORG_123)).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockRedHatPrincipal("123")
  void forceSyncForOrgWorksFailsWithRhPrincipal() throws Exception {
    mvc.perform(post(SYNC_ORG_123)).andExpect(status().isForbidden());
  }
}
