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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilterModifyingConfigurer;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.security.WithMockPskPrincipal;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.subscription.SubscriptionPruneController;
import org.candlepin.subscriptions.subscription.SubscriptionSyncController;
import org.candlepin.subscriptions.util.OfferingProductTagLookupService;
import org.candlepin.subscriptions.utilization.admin.api.model.AwsUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.RhmUsageContext;
import org.candlepin.subscriptions.utilization.admin.api.model.RpcResponse;
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

  private static final String SYNC_ORG_123 = "/internal/subscriptions/sync/org/123";

  private final OffsetDateTime defaultEndDate =
      OffsetDateTime.of(2022, 7, 22, 8, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime defaultLookUpDate =
      OffsetDateTime.of(2022, 6, 22, 8, 0, 0, 0, ZoneOffset.UTC);

  @MockBean SubscriptionSyncController syncController;
  @MockBean SubscriptionPruneController subscriptionPruneController;

  @MockBean OfferingSyncController offeringSync;

  @MockBean CapacityReconciliationController capacityReconciliationController;
  @MockBean MetricMapper metricMapper;
  @MockBean OfferingProductTagLookupService offeringProductTagLookupService;
  @Autowired SecurityProperties properties;
  @Autowired WebApplicationContext context;
  @Autowired InternalSubscriptionResource resource;
  @Autowired MeterRegistry meterRegistry;
  @Autowired ApplicationProperties applicationProperties;

  private MockMvc mvc;

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
    assertEquals(new RpcResponse(), resource.forceSyncSubscriptionsForOrg("123"));
  }

  @Test
  void incrementsMissingCounter_WhenAccountNumberPresent() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(
            meterRegistry,
            syncController,
            properties,
            subscriptionPruneController,
            offeringSync,
            capacityReconciliationController,
            metricMapper,
            applicationProperties,
            offeringProductTagLookupService);
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            resource.getAwsUsageContext(
                defaultLookUpDate, "rosa", null, "Premium", "Production", "123"));
    Counter counter = meterRegistry.counter("swatch_missing_aws_subscription");
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsMissingCounter_WhenOrgIdPresent() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(
            meterRegistry,
            syncController,
            properties,
            subscriptionPruneController,
            offeringSync,
            capacityReconciliationController,
            metricMapper,
            applicationProperties,
            offeringProductTagLookupService);
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            resource.getAwsUsageContext(
                defaultLookUpDate, "rosa", "org123", "Premium", "Production", "123"));
    Counter counter = meterRegistry.counter("swatch_missing_aws_subscription");
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsAmbiguousCounter_WhenOrgIdPresent() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(
            meterRegistry,
            syncController,
            properties,
            subscriptionPruneController,
            offeringSync,
            capacityReconciliationController,
            metricMapper,
            applicationProperties,
            offeringProductTagLookupService);
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(defaultEndDate);
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(defaultEndDate);
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));
    AwsUsageContext awsUsageContext =
        resource.getAwsUsageContext(
            defaultLookUpDate, "rosa", "org123", "Premium", "Production", "123");
    Counter counter = meterRegistry.counter("swatch_ambiguous_aws_subscription");
    assertEquals(1.0, counter.count());
    assertEquals("foo1", awsUsageContext.getProductCode());
    assertEquals("foo2", awsUsageContext.getCustomerId());
    assertEquals("foo3", awsUsageContext.getAwsSellerAccountId());
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
              resource.getAwsUsageContext(
                  lookupDate, "rosa", "org123", "Premium", "Production", "123");
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
    AwsUsageContext awsUsageContext =
        resource.getAwsUsageContext(lookupDate, "rosa", "org123", "Premium", "Production", "123");
    assertEquals("bar1", awsUsageContext.getProductCode());
    assertEquals("bar2", awsUsageContext.getCustomerId());
    assertEquals("bar3", awsUsageContext.getAwsSellerAccountId());
  }

  @Test
  void azureUsageContextEncodesAttributes() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    Subscription sub = new Subscription();
    sub.setBillingProviderId("resourceId;planId;offerId");
    sub.setEndDate(endDate);
    when(syncController.findSubscriptions(any(), any(), any(), any())).thenReturn(List.of(sub));
    var azureUsageContext =
        resource.getAzureMarketplaceContext(
            endDate, "BASILISK", "org123", "Premium", "Production", "123");
    assertEquals("resourceId", azureUsageContext.getAzureResourceId());
    assertEquals("planId", azureUsageContext.getPlanId());
    assertEquals("offerId", azureUsageContext.getOfferId());
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
    mvc.perform(post(SYNC_ORG_123).header("Origin", "console.redhat.com"))
        .andExpect(status().isNotFound());
  }

  @Test
  void forceSyncForOrgWorksFailsWithNoPrincipal() throws Exception {
    mvc.perform(post(SYNC_ORG_123).header("Origin", "console.redhat.com"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockRedHatPrincipal("123")
  void forceSyncForOrgWorksFailsWithRhPrincipal() throws Exception {
    mvc.perform(post(SYNC_ORG_123).header("Origin", "console.redhat.com"))
        .andExpect(status().isForbidden());
  }

  @Test
  void incrementsRhmMissingSubscriptionsCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(
            meterRegistry,
            syncController,
            properties,
            subscriptionPruneController,
            offeringSync,
            capacityReconciliationController,
            metricMapper,
            applicationProperties,
            offeringProductTagLookupService);

    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    OffsetDateTime now = OffsetDateTime.now();
    String sla = ServiceLevel.PREMIUM.toString();
    String usage = Usage.PRODUCTION.toString();
    assertThrows(
        NotFoundException.class,
        () -> {
          resource.getRhmUsageContext("org123", now, "productId", sla, usage);
        });

    Counter counter = meterRegistry.counter("rhsm-subscriptions.marketplace.missing.subscription");
    assertEquals(1.0, counter.count());
  }

  @Test
  void incrementsRhmAmbiguousSubscriptionsCounter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    InternalSubscriptionResource resource =
        new InternalSubscriptionResource(
            meterRegistry,
            syncController,
            properties,
            subscriptionPruneController,
            offeringSync,
            capacityReconciliationController,
            metricMapper,
            applicationProperties,
            offeringProductTagLookupService);

    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("account123");
    sub1.setStartDate(OffsetDateTime.now());
    sub1.setEndDate(sub1.getStartDate().plusMonths(1));

    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("account123");
    sub2.setStartDate(OffsetDateTime.now());
    sub2.setEndDate(sub2.getStartDate().plusMonths(1));

    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    RhmUsageContext rhmUsageContext =
        resource.getRhmUsageContext(
            "org123",
            OffsetDateTime.now(),
            "productId",
            ServiceLevel.PREMIUM.toString(),
            Usage.PRODUCTION.toString());

    Counter counter =
        meterRegistry.counter("rhsm-subscriptions.marketplace.ambiguous.subscription");
    assertEquals(1.0, counter.count());
    assertEquals("account123", rhmUsageContext.getRhSubscriptionId());
  }

  @Test
  void shouldThrowSubscriptionsExceptionForAmbiguousAzureBillingAccountId() {
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("resourceId;planId;offerId");
    sub1.setBillingAccountId("azureTenantId");
    sub1.setEndDate(OffsetDateTime.now());
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("resourceId2;planId;offerId");
    sub2.setBillingAccountId("azureTenantId");
    sub2.setEndDate(OffsetDateTime.now());
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    var lookupDate = OffsetDateTime.now().minusMinutes(30);
    var exception =
        assertThrows(
            SubscriptionsException.class,
            () -> {
              resource.getAzureMarketplaceContext(
                  lookupDate,
                  "rosa",
                  "org123",
                  "Premium",
                  "Production",
                  "azureTenantId;azureSubscriptionId");
            });

    assertEquals(
        ErrorCode.SUBSCRIPTION_CANNOT_BE_DETERMINED.getDescription(),
        exception.getCode().getDescription());
  }

  @Test
  void testShouldReturnSubscriptionWithPartialBillingAccountIdMatch() {
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("resourceId1;planId;offerId");
    sub1.setBillingAccountId("azureTenantId");
    sub1.setEndDate(OffsetDateTime.now());
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("resourceId2;planId;offerId");
    sub2.setBillingAccountId("azureTenantId;azureSubscriptionId2");
    sub2.setEndDate(OffsetDateTime.now());
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    var lookupDate = OffsetDateTime.now().minusMinutes(30);
    var azureUsageContext =
        resource.getAzureMarketplaceContext(
            lookupDate, "BASILISK", "org123", "Premium", "Production", "azureTenantId");
    assertEquals("resourceId1", azureUsageContext.getAzureResourceId());
  }

  @Test
  void testShouldReturnSubscriptionWithExactBillingAccountIdMatch() {
    Subscription sub1 = new Subscription();
    sub1.setBillingProviderId("resourceId1;planId;offerId");
    sub1.setBillingAccountId("azureTenantId");
    sub1.setEndDate(OffsetDateTime.now());
    Subscription sub2 = new Subscription();
    sub2.setBillingProviderId("resourceId2;planId;offerId");
    sub2.setBillingAccountId("azureTenantId;azureSubscriptionId2");
    sub2.setEndDate(OffsetDateTime.now());
    when(syncController.findSubscriptions(any(), any(), any(), any()))
        .thenReturn(List.of(sub1, sub2));

    var lookupDate = OffsetDateTime.now().minusMinutes(30);
    var azureUsageContext =
        resource.getAzureMarketplaceContext(
            lookupDate,
            "BASILISK",
            "org123",
            "Premium",
            "Production",
            "azureTenantId;azureSubscriptionId2");
    assertEquals("resourceId2", azureUsageContext.getAzureResourceId());
  }
}
