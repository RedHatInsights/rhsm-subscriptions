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
package org.candlepin.subscriptions.rhmarketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.rhmarketplace.api.model.BatchStatus;
import org.candlepin.subscriptions.rhmarketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

class RhMarketplaceProducerTest {

  @Test
  void testMarketplaceProducerRetry() throws Exception {
    RetryTemplate retryTemplate = new RetryTemplateBuilder().maxAttempts(2).noBackoff().build();
    RhMarketplaceService rhMarketplaceService = mock(RhMarketplaceService.class);
    MeterRegistry registry = new SimpleMeterRegistry();
    RhMarketplaceProducer rhMarketplaceProducer =
        new RhMarketplaceProducer(
            rhMarketplaceService, retryTemplate, registry, new RhMarketplaceProperties());
    var rejectedCounter = registry.counter("rhsm-subscriptions.rh-marketplace.batch.rejected");

    when(rhMarketplaceService.submitUsageEvents(any())).thenThrow(SubscriptionsException.class);

    var usageRequest = new UsageRequest();

    rhMarketplaceProducer.submitUsageRequest(usageRequest);

    verify(rhMarketplaceService, times(2)).submitUsageEvents(any());
    assertEquals(1.0, rejectedCounter.count());
  }

  @Test
  void testMarketplaceProducerRecordsSuccessfulBatch() throws ApiException {
    RetryTemplate retryTemplate = new RetryTemplateBuilder().maxAttempts(2).noBackoff().build();
    RhMarketplaceService rhMarketplaceService = mock(RhMarketplaceService.class);
    MeterRegistry registry = new SimpleMeterRegistry();
    RhMarketplaceProducer rhMarketplaceProducer =
        new RhMarketplaceProducer(
            rhMarketplaceService, retryTemplate, registry, new RhMarketplaceProperties());
    var acceptedCounter = registry.counter("rhsm-subscriptions.rh-marketplace.batch.accepted");

    when(rhMarketplaceService.submitUsageEvents(any()))
        .thenReturn(
            new StatusResponse()
                .status("inprogress")
                .addDataItem(new BatchStatus().batchId("foo")));
    when(rhMarketplaceService.getUsageBatchStatus("foo"))
        .thenReturn(new StatusResponse().status("accepted"));

    var usageRequest = new UsageRequest();
    rhMarketplaceProducer.submitUsageRequest(usageRequest);

    assertEquals(1.0, acceptedCounter.count());
  }

  @Test
  void testMarketplaceProducerRecordsUnverifiedBatch() throws ApiException {
    RetryTemplate retryTemplate = new RetryTemplateBuilder().maxAttempts(2).noBackoff().build();
    RhMarketplaceService rhMarketplaceService = mock(RhMarketplaceService.class);
    MeterRegistry registry = new SimpleMeterRegistry();
    RhMarketplaceProducer rhMarketplaceProducer =
        new RhMarketplaceProducer(
            rhMarketplaceService, retryTemplate, registry, new RhMarketplaceProperties());
    var unverifiedCounter = registry.counter("rhsm-subscriptions.rh-marketplace.batch.unverified");

    when(rhMarketplaceService.submitUsageEvents(any()))
        .thenReturn(
            new StatusResponse()
                .status("inprogress")
                .addDataItem(new BatchStatus().batchId("foo")));
    when(rhMarketplaceService.getUsageBatchStatus("foo"))
        .thenReturn(new StatusResponse().status("inprogress"));

    var usageRequest = new UsageRequest();
    rhMarketplaceProducer.submitUsageRequest(usageRequest);

    verify(rhMarketplaceService, times(2)).getUsageBatchStatus("foo");
    assertEquals(1.0, unverifiedCounter.count());
  }

  @Test
  void testMarketplaceProducerRecordsRejectedBatch() throws ApiException {
    RetryTemplate retryTemplate = new RetryTemplateBuilder().maxAttempts(2).noBackoff().build();
    RhMarketplaceService rhMarketplaceService = mock(RhMarketplaceService.class);
    MeterRegistry registry = new SimpleMeterRegistry();
    RhMarketplaceProducer rhMarketplaceProducer =
        new RhMarketplaceProducer(
            rhMarketplaceService, retryTemplate, registry, new RhMarketplaceProperties());
    var rejectedCounter = registry.counter("rhsm-subscriptions.rh-marketplace.batch.rejected");

    when(rhMarketplaceService.submitUsageEvents(any()))
        .thenReturn(
            new StatusResponse()
                .status("inprogress")
                .addDataItem(new BatchStatus().batchId("foo")));
    when(rhMarketplaceService.getUsageBatchStatus("foo"))
        .thenReturn(new StatusResponse().status("failed"));

    var usageRequest = new UsageRequest();
    rhMarketplaceProducer.submitUsageRequest(usageRequest);

    assertEquals(1.0, rejectedCounter.count());
  }

  @Test
  void testMarketplaceSkipsVerificationIfAmendmentRejected() throws ApiException {
    RetryTemplate retryTemplate = new RetryTemplateBuilder().maxAttempts(2).noBackoff().build();
    RhMarketplaceService rhMarketplaceService = mock(RhMarketplaceService.class);
    MeterRegistry registry = new SimpleMeterRegistry();
    var properties = new RhMarketplaceProperties();
    properties.setAmendmentNotSupportedMarker("(amendments) is not available");
    RhMarketplaceProducer rhMarketplaceProducer =
        new RhMarketplaceProducer(rhMarketplaceService, retryTemplate, registry, properties);
    when(rhMarketplaceService.submitUsageEvents(any()))
        .thenReturn(
            new StatusResponse()
                .status("failed")
                .addDataItem(
                    new BatchStatus()
                        .batchId("foo")
                        .message(
                            "Requested feature (amendments) is not available on this environment")));

    var usageRequest = new UsageRequest();
    rhMarketplaceProducer.submitUsageRequest(usageRequest);

    verify(rhMarketplaceService, times(1)).submitUsageEvents(any());
    verifyNoMoreInteractions(rhMarketplaceService);
  }
}
