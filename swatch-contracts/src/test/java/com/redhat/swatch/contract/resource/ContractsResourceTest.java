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

import static com.redhat.swatch.contract.resource.ContractsResource.FEATURE_NOT_ENABLED_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.product.api.resources.ApiException;
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.openapi.model.AwsUsageContext;
import com.redhat.swatch.contract.openapi.model.RhmUsageContext;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.service.EnabledOrgsProducer;
import com.redhat.swatch.contract.service.OfferingProductTagLookupService;
import com.redhat.swatch.contract.service.OfferingSyncService;
import com.redhat.swatch.contract.service.SubscriptionSyncService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@TestSecurity(
    user = "placeholder",
    roles = {"service"})
class ContractsResourceTest {

  private static final String SKU = "mw123";
  private static final String ORG_ID = "org123";
  private final OffsetDateTime defaultEndDate =
      OffsetDateTime.of(2022, 7, 22, 8, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime defaultLookUpDate =
      OffsetDateTime.of(2022, 6, 22, 8, 0, 0, 0, ZoneOffset.UTC);

  @InjectMock ApplicationConfiguration applicationConfiguration;
  @InjectMock EnabledOrgsProducer enabledOrgsProducer;
  @InjectMock OfferingSyncService offeringSyncService;
  @InjectMock OfferingProductTagLookupService offeringProductTagLookupService;
  @InjectMock SubscriptionSyncService subscriptionSyncService;
  @InjectMock SubscriptionRepository subscriptionRepository;
  @Inject ContractsResource resource;
  @Inject MeterRegistry meterRegistry;

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
  void testSyncAllOfferings() {
    var result = resource.syncAllOfferings();
    assertNotNull(result);
    verify(offeringSyncService).syncAllOfferings();
  }

  @Test
  void testSyncOffering() {
    var result = resource.syncOffering(SKU);
    assertNotNull(result);
    verify(offeringSyncService).syncOffering(SKU);
  }

  @Test
  void testGetSkuProductTags() {
    resource.getSkuProductTags(SKU);
    verify(offeringProductTagLookupService).findPersistedProductTagsBySku(SKU);
  }

  @Test
  void forceSyncForOrgShouldReturnSuccess() {
    Mockito.doCallRealMethod()
        .when(subscriptionSyncService)
        .forceSyncSubscriptionsForOrgAsync(ORG_ID);
    resource.forceSyncSubscriptionsForOrg(ORG_ID);
    verify(subscriptionSyncService).forceSyncSubscriptionsForOrg(ORG_ID, false);
  }

  @Test
  void testSyncSkuErrorResult() {
    when(offeringSyncService.syncOffering(any(String.class)))
        .thenReturn(SyncResult.SKIPPED_NOT_FOUND);
    assertThrows(NotFoundException.class, () -> resource.syncOffering("TEST_SKU"));

    when(offeringSyncService.syncOffering(any(String.class)))
        .thenReturn(SyncResult.SKIPPED_DENYLISTED);
    assertThrows(ForbiddenException.class, () -> resource.syncOffering("TEST_SKU"));

    RuntimeException rtex = mock(RuntimeException.class);
    ApiException ae = mock(ApiException.class);
    when(rtex.getCause()).thenReturn(ae);
    Response r = mock(Response.class);
    when(ae.getResponse()).thenReturn(r);

    when(r.getStatus()).thenReturn(404);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    assertThrows(NotFoundException.class, () -> resource.syncOffering("TEST_SKU"));

    when(r.getStatus()).thenReturn(403);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    assertThrows(ForbiddenException.class, () -> resource.syncOffering("TEST_SKU"));

    when(r.getStatus()).thenReturn(400);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    assertThrows(BadRequestException.class, () -> resource.syncOffering("TEST_SKU"));

    when(r.getStatus()).thenReturn(0);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    assertThrows(InternalServerErrorException.class, () -> resource.syncOffering("TEST_SKU"));
  }

  @Test
  void incrementsMissingCounter_WhenAccountNumberPresent() {
    Counter counter = meterRegistry.counter("swatch_missing_subscriptions", "provider", "aws");
    var initialCount = counter.count();
    when(subscriptionRepository.findByCriteria(any())).thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            resource.getAwsUsageContext(
                defaultLookUpDate, "rosa", null, "Premium", "Production", "123"));
    assertEquals(1.0, counter.count() - initialCount);
  }

  @Test
  void incrementsMissingCounter_WhenOrgIdPresent() {
    Counter counter = meterRegistry.counter("swatch_missing_subscriptions", "provider", "aws");
    var initialCount = counter.count();
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(Collections.emptyList());
    assertThrows(
        NotFoundException.class,
        () ->
            resource.getAwsUsageContext(
                defaultLookUpDate, "rosa", "org123", "Premium", "Production", "123"));
    assertEquals(1.0, counter.count() - initialCount);
  }

  @Test
  void incrementsAmbiguousCounter_WhenOrgIdPresent() {
    Counter counter = meterRegistry.counter("swatch_ambiguous_subscriptions", "provider", "aws");
    var initialCount = counter.count();
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(defaultEndDate);
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(defaultEndDate);
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));
    AwsUsageContext awsUsageContext =
        resource.getAwsUsageContext(
            defaultLookUpDate, "rosa", "org123", "Premium", "Production", "123");

    assertEquals(1.0, counter.count() - initialCount);
    assertEquals("foo1", awsUsageContext.getProductCode());
    assertEquals("foo2", awsUsageContext.getCustomerId());
    assertEquals("foo3", awsUsageContext.getAwsSellerAccountId());
  }

  @Test
  void shouldThrowSubscriptionsExceptionForTerminatedSubscription_WhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(endDate);
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1));

    var lookupDate = endDate.plusMinutes(30);
    var exception =
        assertThrows(
            ServiceException.class,
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
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(endDate);
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(endDate.plusMinutes(45));
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));
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
    SubscriptionEntity sub = new SubscriptionEntity();
    sub.setBillingProviderId("resourceId;planId;offerId");
    sub.setEndDate(endDate);
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub));
    var azureUsageContext =
        resource.getAzureMarketplaceContext(
            endDate, "BASILISK", "org123", "Premium", "Production", "123");
    assertEquals("resourceId", azureUsageContext.getAzureResourceId());
    assertEquals("planId", azureUsageContext.getPlanId());
    assertEquals("offerId", azureUsageContext.getOfferId());
  }

  @Test
  void incrementsRhmMissingSubscriptionsCounter() {
    Counter counter = meterRegistry.counter("swatch_missing_subscriptions", "provider", "red hat");
    var initialValue = counter.count();
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(Collections.emptyList());

    OffsetDateTime now = OffsetDateTime.now();
    String sla = ServiceLevel.PREMIUM.toString();
    String usage = Usage.PRODUCTION.toString();
    assertThrows(
        NotFoundException.class,
        () -> {
          resource.getRhmUsageContext("org123", now, "productId", sla, usage);
        });

    assertEquals(1.0, counter.count() - initialValue);
  }

  @Test
  void incrementsRhmAmbiguousSubscriptionsCounter() {
    Counter counter =
        meterRegistry.counter("swatch_ambiguous_subscriptions", "provider", "red hat");
    var initialValue = counter.count();

    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("account123");
    sub1.setStartDate(OffsetDateTime.now());
    sub1.setEndDate(sub1.getStartDate().plusMonths(1));

    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("account123");
    sub2.setStartDate(OffsetDateTime.now());
    sub2.setEndDate(sub2.getStartDate().plusMonths(1));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));

    RhmUsageContext rhmUsageContext =
        resource.getRhmUsageContext(
            "org123",
            OffsetDateTime.now(),
            "productId",
            ServiceLevel.PREMIUM.toString(),
            Usage.PRODUCTION.toString());

    assertEquals(1.0, counter.count() - initialValue);
    assertEquals("account123", rhmUsageContext.getRhSubscriptionId());
  }

  @Test
  void testShouldReturnSubscriptionWithExactBillingAccountIdMatch() {
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("resourceId1;planId;offerId");
    sub1.setBillingAccountId("azureSubscriptionId1");
    sub1.setEndDate(OffsetDateTime.now());
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("resourceId2;planId;offerId");
    sub2.setBillingAccountId("azureSubscriptionId2");
    sub2.setEndDate(OffsetDateTime.now());
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub2));

    var lookupDate = OffsetDateTime.now().minusMinutes(30);
    var azureUsageContext =
        resource.getAzureMarketplaceContext(
            lookupDate, "BASILISK", "org123", "Premium", "Production", "azureSubscriptionId2");
    assertEquals("resourceId2", azureUsageContext.getAzureResourceId());
  }
}
