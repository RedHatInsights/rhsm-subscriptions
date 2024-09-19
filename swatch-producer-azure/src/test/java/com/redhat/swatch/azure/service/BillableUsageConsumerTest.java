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

import static com.redhat.swatch.azure.test.resources.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.exception.AzureUsageContextLookupException;
import com.redhat.swatch.azure.exception.DefaultApiException;
import com.redhat.swatch.azure.exception.SubscriptionCanNotBeDeterminedException;
import com.redhat.swatch.azure.exception.SubscriptionRecentlyTerminatedException;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.contracts.api.model.AzureUsageContext;
import com.redhat.swatch.clients.contracts.api.model.Error;
import com.redhat.swatch.clients.contracts.api.model.Errors;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.Usage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BillableUsageConsumerTest {

  private static final String BASILISK = "BASILISK";
  private static final String INSTANCE_HOURS = "INSTANCE_HOURS";
  private static final String STORAGE_GIB_MONTHS = "STORAGE_GIBIBYTE_MONTHS";

  private static final BillableUsageAggregate BASILISK_INSTANCE_HOURS_RECORD =
      createAggregate(BASILISK, INSTANCE_HOURS, OffsetDateTime.now(Clock.systemUTC()), 42);

  private static final BillableUsageAggregate BASILISK_INSTANCE_HOURS_RECORD_OLD =
      createAggregate(
          BASILISK, INSTANCE_HOURS, OffsetDateTime.now(Clock.systemUTC()).minusDays(10), 42);

  private static final BillableUsageAggregate BASILISK_STORAGE_GIB_MONTHS_RECORD =
      createAggregate(
          BASILISK, STORAGE_GIB_MONTHS, OffsetDateTime.now(Clock.systemUTC()).minusHours(73), 42);

  public static final AzureUsageContext MOCK_AZURE_USAGE_CONTEXT =
      new AzureUsageContext().azureResourceId("id").offerId("product").planId("plan");
  public static final UsageEventOkResponse USAGE_EVENT_RESPONSE = getDefaultUsageEventResponse();

  @InjectMock @RestClient DefaultApi contractsApi;
  @InjectMock AzureMarketplaceService marketplaceService;

  @Inject MeterRegistry meterRegistry;
  Counter acceptedCounter;
  Counter rejectedCounter;
  Counter ignoredCounter;
  @Inject AzureBillableUsageAggregateConsumer consumer;
  @InjectMock BillableUsageStatusProducer statusProducer;

  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

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
    var aggregate = createAggregate(BASILISK, INSTANCE_HOURS, OffsetDateTime.now(), 10);
    var key =
        new BillableUsageAggregateKey(
            "testOrg",
            BASILISK,
            INSTANCE_HOURS,
            BillableUsage.Sla.PREMIUM.value(),
            Usage.PRODUCTION.getValue(),
            BillableUsage.BillingProvider.RED_HAT.value(),
            "testBillingAccountId");
    aggregate.setAggregateKey(key);
    consumer.process(aggregate);
    verifyNoInteractions(contractsApi, marketplaceService);
  }

  @Test
  void shouldLookupAzureContextOnApplicableSnapshot() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AzureUsageContext());
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(contractsApi).getAzureMarketplaceContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldSendUsageForApplicableSnapshot() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(marketplaceService).sendUsageEventToAzureMarketplace(any(UsageEvent.class));
  }

  @Test
  void shouldSendStatusMessageToIfSubscriptionNotFound() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(new SubscriptionCanNotBeDeterminedException(null));
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND.equals(
                            usage.getErrorCode())));
  }

  @Test
  void shouldInactiveErrorCodeIfSnapshotDateIsOutOfTheTimeWindow() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(new SubscriptionCanNotBeDeterminedException(null));
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD_OLD);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.INACTIVE.equals(usage.getErrorCode())));
  }

  @Test
  void shouldInactiveErrorCodeIfSubscriptionRecentlyTerminated() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(new SubscriptionRecentlyTerminatedException(null));
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD_OLD);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.SUBSCRIPTION_TERMINATED.equals(
                            usage.getErrorCode())));
  }

  @Test
  void shouldSkipMessageButSendStatusIfAzureContextCannotBeLookedUp() throws ApiException {
    var aggregate =
        createAggregate("rosa", INSTANCE_HOURS, OffsetDateTime.now(Clock.systemUTC()), 42);
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(AzureUsageContextLookupException.class);
    consumer.process(aggregate);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.USAGE_CONTEXT_LOOKUP.equals(
                            usage.getErrorCode())));
    verifyNoInteractions(marketplaceService);
  }

  @Test
  void shouldSkipMessageIfUnknownAzureDimensionCannotBeLookedUp() {
    var aggregate =
        createAggregate("foobar", INSTANCE_HOURS, OffsetDateTime.now(Clock.systemUTC()), 42);
    consumer.process(aggregate);
    verifyNoInteractions(contractsApi, marketplaceService);
  }

  @Test
  void shouldFindStorageAzureDimension() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AzureUsageContext());
    consumer.process(BASILISK_STORAGE_GIB_MONTHS_RECORD);
    verify(contractsApi).getAzureMarketplaceContext(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldIncrementAcceptedCounterIfSuccessful() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    assertEquals(1.0, acceptedCounter.count());
  }

  @Test
  void shouldSendSuccessStatusIfSuccessful() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.SUCCEEDED.equals(usage.getStatus())
                        && Objects.isNull(usage.getErrorCode())));
  }

  @Test
  void shouldIncrementFailureCounterOnError() throws ApiException {
    double current = rejectedCounter.count();
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    when(marketplaceService.sendUsageEventToAzureMarketplace(any(UsageEvent.class)))
        .thenThrow(AzureMarketplaceRequestFailedException.class);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    assertEquals(current + 1, rejectedCounter.count());
  }

  @Test
  void shouldSendFailureStatusOnFailure() throws ApiException {
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    when(marketplaceService.sendUsageEventToAzureMarketplace(any(UsageEvent.class)))
        .thenThrow(AzureMarketplaceRequestFailedException.class);
    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verify(statusProducer)
        .emitStatus(
            argThat(
                usage ->
                    BillableUsage.Status.FAILED.equals(usage.getStatus())
                        && BillableUsage.ErrorCode.UNKNOWN.equals(usage.getErrorCode())));
  }

  @Test
  void shouldNotMakeAzureUsageRequestWhenDryRunEnabled() throws ApiException {
    AzureBillableUsageAggregateConsumer azureConsumer =
        new AzureBillableUsageAggregateConsumer(
            meterRegistry,
            contractsApi,
            marketplaceService,
            statusProducer,
            Optional.of(true),
            null);
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenReturn(MOCK_AZURE_USAGE_CONTEXT);
    azureConsumer.process(BASILISK_INSTANCE_HOURS_RECORD);
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
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
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
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    consumer.process(BASILISK_INSTANCE_HOURS_RECORD);
    verifyNoInteractions(marketplaceService);
  }

  @Test
  void shouldThrowSubscriptionNotFoundException() throws ApiException {
    var response = Response.status(Status.NOT_FOUND.getStatusCode()).build();
    var exception = new DefaultApiException(response, null);
    when(contractsApi.getAzureMarketplaceContext(any(), any(), any(), any(), any(), any()))
        .thenThrow(exception);

    assertThrows(
        SubscriptionCanNotBeDeterminedException.class,
        () -> {
          consumer.lookupAzureUsageContext(BASILISK_INSTANCE_HOURS_RECORD);
        });
  }

  private static UsageEventOkResponse getDefaultUsageEventResponse() {
    var response = new UsageEventOkResponse();
    response.setStatus(UsageEventStatusEnum.ACCEPTED);
    return response;
  }

  private static BillableUsageAggregate createAggregate(
      String productId, String metricId, OffsetDateTime timestamp, double totalValue) {
    var aggregate = new BillableUsageAggregate();
    aggregate.setWindowTimestamp(timestamp);
    aggregate.setSnapshotDates(Set.of(timestamp, timestamp.plusDays(5)));
    aggregate.setTotalValue(new BigDecimal(totalValue));
    aggregate.setRemittanceUuids(List.of(UUID.randomUUID().toString()));
    var key =
        new BillableUsageAggregateKey(
            "testOrg",
            productId,
            metricId,
            BillableUsage.Sla.PREMIUM.value(),
            Usage.PRODUCTION.getValue(),
            BillableUsage.BillingProvider.AZURE.value(),
            "testBillingAccountId");
    aggregate.setAggregateKey(key);
    return aggregate;
  }
}
