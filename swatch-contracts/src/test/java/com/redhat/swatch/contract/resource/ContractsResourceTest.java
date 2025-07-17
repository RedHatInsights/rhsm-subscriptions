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
import static com.redhat.swatch.contract.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.CUSTOMER_IDENTITY_HEADER;
import static com.redhat.swatch.contract.service.UsageContextSubscriptionProvider.AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME;
import static com.redhat.swatch.contract.service.UsageContextSubscriptionProvider.MISSING_SUBSCRIPTIONS_COUNTER_NAME;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.product.api.resources.ApiException;
import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.openapi.model.AwsUsageContext;
import com.redhat.swatch.contract.openapi.model.AzureUsageContext;
import com.redhat.swatch.contract.openapi.model.OfferingResponse;
import com.redhat.swatch.contract.openapi.model.RpcResponse;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.service.EnabledOrgsProducer;
import com.redhat.swatch.contract.service.OfferingProductTagLookupService;
import com.redhat.swatch.contract.service.OfferingSyncService;
import com.redhat.swatch.contract.service.SubscriptionSyncService;
import com.redhat.swatch.contract.test.resources.DisableRbacResource;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@TestProfile(DisableRbacResource.class)
class ContractsResourceTest {

  private static final String SKU = "mw123";
  private static final String ORG_ID = "org123";
  private static final String ROSA = "rosa";
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
  @Inject MeterRegistry meterRegistry;

  @BeforeEach
  void setup() {
    meterRegistry.clear();
  }

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsNotEnabled() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(false);
    RpcResponse response =
        given()
            .queryParams("forceSync", "false")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .put("/api/swatch-contracts/internal/rpc/subscriptions/sync")
            .then()
            .statusCode(200)
            .extract()
            .as(RpcResponse.class);

    assertEquals(FEATURE_NOT_ENABLED_MESSAGE, response.getResult());
    verify(enabledOrgsProducer, times(0)).sendTaskForSubscriptionsSync();
  }

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsNotEnabledButForce() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(false);
    RpcResponse response =
        given()
            .queryParams("forceSync", "true")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .put("/api/swatch-contracts/internal/rpc/subscriptions/sync")
            .then()
            .statusCode(200)
            .extract()
            .as(RpcResponse.class);

    assertNull(response.getResult());
    verify(enabledOrgsProducer).sendTaskForSubscriptionsSync();
  }

  @Test
  void testSyncAllSubscriptionsWhenFeatureIsEnabled() {
    when(applicationConfiguration.isSubscriptionSyncEnabled()).thenReturn(true);
    RpcResponse response =
        given()
            .queryParams("forceSync", "false")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .put("/api/swatch-contracts/internal/rpc/subscriptions/sync")
            .then()
            .statusCode(200)
            .extract()
            .as(RpcResponse.class);

    assertNull(response.getResult());
    verify(enabledOrgsProducer).sendTaskForSubscriptionsSync();
  }

  @Test
  void testSyncAllOfferings() {
    OfferingResponse response =
        given()
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .put("/api/swatch-contracts/internal/rpc/offerings/sync")
            .then()
            .statusCode(200)
            .extract()
            .as(OfferingResponse.class);

    assertNotNull(response);
    verify(offeringSyncService).syncAllOfferings();
  }

  @Test
  void testSyncOffering() {
    OfferingResponse response =
        given()
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", SKU))
            .then()
            .statusCode(200)
            .extract()
            .as(OfferingResponse.class);

    assertNotNull(response);
    verify(offeringSyncService).syncOffering(SKU);
  }

  @Test
  void testGetSkuProductTags() {
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get(String.format("/api/swatch-contracts/internal/offerings/%s/product_tags", SKU))
        .then()
        .statusCode(204);

    verify(offeringProductTagLookupService).findPersistedProductTagsBySku(SKU);
  }

  @Test
  void forceSyncForOrgShouldReturnSuccess() {
    Mockito.doCallRealMethod()
        .when(subscriptionSyncService)
        .forceSyncSubscriptionsForOrgAsync(ORG_ID);
    given()
        .queryParams("forceSync", "false")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/subscriptions/sync/org/%s", ORG_ID))
        .then()
        .statusCode(200)
        .extract()
        .as(RpcResponse.class);

    verify(subscriptionSyncService).forceSyncSubscriptionsForOrg(ORG_ID, false);
  }

  @Test
  void testSyncSkuErrorResult() {
    when(offeringSyncService.syncOffering(any(String.class)))
        .thenReturn(SyncResult.SKIPPED_NOT_FOUND);
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(404);

    when(offeringSyncService.syncOffering(any(String.class)))
        .thenReturn(SyncResult.SKIPPED_DENYLISTED);
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(403);

    RuntimeException rtex = mock(RuntimeException.class);
    ApiException ae = mock(ApiException.class);
    when(rtex.getCause()).thenReturn(ae);
    Response r = mock(Response.class);
    when(ae.getResponse()).thenReturn(r);

    when(r.getStatus()).thenReturn(404);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(404);

    when(r.getStatus()).thenReturn(403);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(403);

    when(r.getStatus()).thenReturn(400);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(400);

    when(r.getStatus()).thenReturn(0);
    doThrow(rtex).when(offeringSyncService).syncOffering(any(String.class));
    given()
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .put(String.format("/api/swatch-contracts/internal/rpc/offerings/sync/%s", "TEST_SKU"))
        .then()
        .statusCode(500);
  }

  @Test
  void incrementsMissingCounterWhenAccountNumberPresent() {
    when(subscriptionRepository.findByCriteria(any())).thenReturn(Collections.emptyList());
    given()
        .queryParams(
            "date",
            defaultLookUpDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "productId",
            ROSA,
            "sla",
            ServiceLevelType.PREMIUM.toString(),
            "usage",
            UsageType.PRODUCTION.toString(),
            "awsAccountId",
            "123")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get("/api/swatch-contracts/internal/subscriptions/awsUsageContext")
        .then()
        .statusCode(404);

    thenMissingSubscriptionsMetricIs("aws", 1.0);
  }

  @Test
  void incrementsMissingCounterWhenOrgIdPresent() {
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(Collections.emptyList());
    given()
        .queryParams(
            "orgId",
            ORG_ID,
            "date",
            defaultLookUpDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "productId",
            ROSA,
            "sla",
            ServiceLevelType.PREMIUM.toString(),
            "usage",
            UsageType.PRODUCTION.toString(),
            "awsAccountId",
            "123")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get("/api/swatch-contracts/internal/subscriptions/awsUsageContext")
        .then()
        .statusCode(404);

    thenMissingSubscriptionsMetricIs("aws", 1.0);
  }

  @Test
  void incrementsAmbiguousCounterWhenOrgIdPresent() {
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(defaultEndDate);
    SubscriptionEntity sub2 = new SubscriptionEntity();
    sub2.setBillingProviderId("bar1;bar2;bar3");
    sub2.setEndDate(defaultEndDate);
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1, sub2));
    AwsUsageContext awsUsageContext =
        given()
            .queryParams(
                "orgId",
                ORG_ID,
                "date",
                defaultLookUpDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "productId",
                ROSA,
                "sla",
                ServiceLevelType.PREMIUM.toString(),
                "usage",
                UsageType.PRODUCTION.toString(),
                "awsAccountId",
                "123")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/internal/subscriptions/awsUsageContext")
            .then()
            .statusCode(200)
            .extract()
            .as(AwsUsageContext.class);

    thenAmbiguousSubscriptionsMetricIs("aws", 1.0);
    assertEquals("foo1", awsUsageContext.getProductCode());
    assertEquals("foo2", awsUsageContext.getCustomerId());
    assertEquals("foo3", awsUsageContext.getAwsSellerAccountId());
  }

  @Test
  void shouldThrowSubscriptionsExceptionForTerminatedSubscriptionWhenOrgIdPresent() {
    var endDate = OffsetDateTime.of(2022, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    SubscriptionEntity sub1 = new SubscriptionEntity();
    sub1.setBillingProviderId("foo1;foo2;foo3");
    sub1.setEndDate(endDate);
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(List.of(sub1));

    var lookupDate = endDate.plusMinutes(30);
    given()
        .queryParams(
            "orgId",
            ORG_ID,
            "date",
            lookupDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "productId",
            ROSA,
            "sla",
            ServiceLevelType.PREMIUM.toString(),
            "usage",
            UsageType.PRODUCTION.toString(),
            "awsAccountId",
            "123")
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get("/api/swatch-contracts/internal/subscriptions/awsUsageContext")
        .then()
        .body("code", equalTo(ErrorCode.SUBSCRIPTION_RECENTLY_TERMINATED.getCode()))
        .statusCode(404);
  }

  @Test
  void shouldReturnActiveSubscriptionAndNotTerminatedWhenOrgIdPresent() {
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
        given()
            .queryParams(
                "orgId",
                ORG_ID,
                "date",
                lookupDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "productId",
                ROSA,
                "sla",
                ServiceLevelType.PREMIUM.toString(),
                "usage",
                UsageType.PRODUCTION.toString(),
                "awsAccountId",
                "123")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/internal/subscriptions/awsUsageContext")
            .then()
            .statusCode(200)
            .extract()
            .as(AwsUsageContext.class);

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
    AzureUsageContext azureUsageContext =
        given()
            .queryParams(
                "orgId",
                ORG_ID,
                "date",
                endDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "productId",
                "BASILISK",
                "sla",
                ServiceLevelType.PREMIUM.toString(),
                "usage",
                UsageType.PRODUCTION.toString(),
                "azureAccountId",
                "123")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/internal/subscriptions/azureUsageContext")
            .then()
            .statusCode(200)
            .extract()
            .as(AzureUsageContext.class);

    assertEquals("resourceId", azureUsageContext.getAzureResourceId());
    assertEquals("planId", azureUsageContext.getPlanId());
    assertEquals("offerId", azureUsageContext.getOfferId());
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
    AzureUsageContext azureUsageContext =
        given()
            .queryParams(
                "orgId",
                ORG_ID,
                "date",
                lookupDate.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "productId",
                "BASILISK",
                "sla",
                ServiceLevelType.PREMIUM.toString(),
                "usage",
                UsageType.PRODUCTION.toString(),
                "azureAccountId",
                "azureSubscriptionId2")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get("/api/swatch-contracts/internal/subscriptions/azureUsageContext")
            .then()
            .statusCode(200)
            .extract()
            .as(AzureUsageContext.class);

    assertEquals("resourceId2", azureUsageContext.getAzureResourceId());
  }

  void thenAmbiguousSubscriptionsMetricIs(String provider, double expected) {
    thenMetricIs(AMBIGUOUS_SUBSCRIPTIONS_COUNTER_NAME, provider, expected);
  }

  void thenMissingSubscriptionsMetricIs(String provider, double expected) {
    thenMetricIs(MISSING_SUBSCRIPTIONS_COUNTER_NAME, provider, expected);
  }

  void thenMetricIs(String metric, String provider, double expected) {
    assertEquals(
        expected, meterRegistry.counter(metric, "provider", provider, "product", ROSA).count());
  }
}
