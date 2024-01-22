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
package com.redhat.swatch.azure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.exception.AzureUsageContextLookupException;
import com.redhat.swatch.azure.exception.DefaultApiException;
import com.redhat.swatch.azure.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.azure.openapi.model.BillableUsage;
import com.redhat.swatch.azure.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AzureUsageContext;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Error;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Errors;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class BillableUsageConsumerTest {

  private static final String INSTANCE_HOURS = "INSTANCE_HOURS";
  private static final String STORAGE_GIB_MONTHS = "STORAGE_GIBIBYTE_MONTHS";

  private static final BillableUsage BASILISK_INSTANCE_HOURS_RECORD =
      new BillableUsage()
          .productId("BASILISK")
          .snapshotDate(OffsetDateTime.MAX)
          .billingProvider(BillingProviderEnum.AZURE)
          .uom(INSTANCE_HOURS)
          .snapshotDate(OffsetDateTime.now(Clock.systemUTC()))
          .value(new BigDecimal("42.0"));

  private static final BillableUsage BASILISK_STORAGE_GIB_MONTHS_RECORD =
      new BillableUsage()
          .productId("BASILISK")
          .snapshotDate(OffsetDateTime.MAX)
          .billingProvider(BillingProviderEnum.AZURE)
          .uom(STORAGE_GIB_MONTHS)
          .snapshotDate(OffsetDateTime.now(Clock.systemUTC()))
          .value(new BigDecimal("42.0"));

  public static final AzureUsageContext MOCK_AZURE_USAGE_CONTEXT =
      new AzureUsageContext()
          .azureResourceId("id")
          .azureTenantId("tenant")
          .offerId("product")
          .planId("plan");
  public static final UsageEventOkResponse USAGE_EVENT_RESPONSE = getDefaultUsageEventResponse();

  @InjectMock @RestClient InternalSubscriptionsApi internalSubscriptionsApi;
  @InjectMock AzureMarketplaceService marketplaceService;

  @Inject MeterRegistry meterRegistry;
  Counter acceptedCounter;
  Counter rejectedCounter;
  Counter ignoredCounter;
  @Inject BillableUsageConsumer consumer;

  @BeforeEach
  void setup() {
    acceptedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_accepted_total");
    rejectedCounter = meterRegistry.counter("swatch_azure_marketplace_batch_rejected_total");
    ignoredCounter = meterRegistry.counter("swatch_azure_marketplace_batch_ignored_total");
    when(marketplaceService.sendUsageEventToAzureMarketplace(any(UsageEvent.class)))
        .thenReturn(USAGE_EVENT_RESPONSE);
  }

  @Test
  void shouldSkipNonAzureSnapshots() {
    BillableUsage usage =
        new BillableUsage()
            .billingProvider(BillingProviderEnum.RED_HAT)
            .snapshotDate(OffsetDateTime.now(Clock.systemUTC()));
    consumer.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, marketplaceService);
  }

  @Test
  void shouldLookupAzureContextOnApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(new AzureUsageContext());
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(internalSubscriptionsApi)
        .getAzureMarketplaceContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldSendUsageForApplicableSnapshot() throws ApiException {
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(marketplaceService).sendUsageEventToAzureMarketplace(any(UsageEvent.class));
  }

  @Test
  void shouldSkipMessageIfAzureContextCannotBeLookedUp() throws ApiException {
    BillableUsage usage =
        new BillableUsage()
            .productId("rosa")
            .billingProvider(BillingProviderEnum.AZURE)
            .uom(INSTANCE_HOURS)
            .snapshotDate(OffsetDateTime.now(Clock.systemUTC()))
            .value(new BigDecimal("42.0"));
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenThrow(AzureUsageContextLookupException.class);
    consumer.process(usage);
    verifyNoInteractions(marketplaceService);
  }

  @Test
  void shouldSkipMessageIfUnknownAzureDimensionCannotBeLookedUp() {
    BillableUsage usage =
        new BillableUsage()
            .productId("foobar")
            .billingProvider(BillingProviderEnum.AZURE)
            .uom(INSTANCE_HOURS)
            .snapshotDate(OffsetDateTime.now(Clock.systemUTC()))
            .value(new BigDecimal("42.0"));
    consumer.process(usage);
    verifyNoInteractions(internalSubscriptionsApi, marketplaceService);
  }

  @Test
  void shouldFindStorageAzureDimension() throws ApiException {
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(new AzureUsageContext());
    consumer.process(BASILISK_STORAGE_GIB_MONTHS_RECORD);
    verify(internalSubscriptionsApi)
        .getAzureMarketplaceContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldIncrementAcceptedCounterIfSuccessful() throws ApiException {
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, acceptedCounter.count());
  }

  @Test
  void shouldIncrementFailureCounterOnError() throws ApiException {
    double current = rejectedCounter.count();
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    when(marketplaceService.sendUsageEventToAzureMarketplace(any(UsageEvent.class)))
        .thenThrow(AzureMarketplaceRequestFailedException.class);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
  }

  @Test
  void shouldNotMakeAzureUsageRequestWhenDryRunEnabled() throws ApiException {
    BillableUsageConsumer consumer =
        new BillableUsageConsumer(
            meterRegistry, internalSubscriptionsApi, marketplaceService, Optional.of(true));
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(marketplaceService);
  }

  @Test
  void shouldThrowSubscriptionTerminatedException() throws ApiException {
    Errors errors = new Errors();
    Error error = new Error();
    error.setCode("SUBSCRIPTIONS1005");
    errors.setErrors(List.of(error));
    var response = Response.serverError().entity(errors).build();
    var exception = new DefaultApiException(response, errors);
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    assertThrows(
        SubscriptionRecentlyTerminatedException.class,
        () -> {
          consumer.lookupAzureUsageContext(BASILISK_INSTANCE_HOURS_RECORD);
        });
  }

  @Test
  void shouldSkipMessageIfSubscriptionRecentlyTerminated() throws ApiException {
    Errors errors = new Errors();
    Error error = new Error();
    error.setCode("SUBSCRIPTIONS1005");
    errors.setErrors(List.of(error));
    var response = Response.serverError().entity(errors).build();
    var exception = new DefaultApiException(response, errors);
    when(internalSubscriptionsApi.getAzureMarketplaceContext(
            any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(marketplaceService);
  }

  private static UsageEventOkResponse getDefaultUsageEventResponse() {
    var response = new UsageEventOkResponse();
    response.setStatus(UsageEventStatusEnum.ACCEPTED);
    return response;
  }
}
