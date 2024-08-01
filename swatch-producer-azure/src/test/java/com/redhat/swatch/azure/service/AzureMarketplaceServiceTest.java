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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.azure.marketplace.api.resources.ApiException;
import com.redhat.swatch.clients.azure.marketplace.api.resources.AzureMarketplaceApi;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AzureMarketplaceServiceTest {

  AzureMarketplaceProperties azureMarketplaceProperties = new AzureMarketplaceProperties();

  AzureMarketplaceClientFactory azureMarketplaceClientFactory;
  AzureMarketplaceApi acceptedClient;
  AzureMarketplaceApi failedClient;
  AzureMarketplaceApi badRequestClient;

  @BeforeEach
  void setup() throws Exception {
    acceptedClient = mock(AzureMarketplaceApi.class);
    failedClient = mock(AzureMarketplaceApi.class);
    badRequestClient = mock(AzureMarketplaceApi.class);
    azureMarketplaceClientFactory = Mockito.mock(AzureMarketplaceClientFactory.class);
    Mockito.when(acceptedClient.submitUsageEvents(any(), any(), any(), any()))
        .thenReturn(new UsageEventOkResponse().status(UsageEventStatusEnum.ACCEPTED));
    Mockito.when(failedClient.submitUsageEvents(any(), any(), any(), any()))
        .thenThrow(new ProcessingException("error"));
    Mockito.when(badRequestClient.submitUsageEvents(any(), any(), any(), any()))
        .thenThrow(new ApiException(Response.status(HttpStatus.SC_BAD_REQUEST).build()));
  }

  @Test
  void testSendUsageEventToMarketplaceAcceptedFirstCredentials() throws Exception {
    Mockito.when(azureMarketplaceClientFactory.createClientForEachTenant())
        .thenReturn(List.of(acceptedClient, failedClient));
    var service =
        new AzureMarketplaceService(azureMarketplaceProperties, azureMarketplaceClientFactory);
    var response = service.sendUsageEventToAzureMarketplace(new UsageEvent());
    assertNotNull(response);
    verify(acceptedClient).submitUsageEvents(any(), any(), any(), any());
    verifyNoInteractions(failedClient);
  }

  @Test
  void testSendUsageEventToMarketplaceAcceptedSecondCredentials() throws Exception {
    Mockito.when(azureMarketplaceClientFactory.createClientForEachTenant())
        .thenReturn(List.of(failedClient, acceptedClient));
    var service =
        new AzureMarketplaceService(azureMarketplaceProperties, azureMarketplaceClientFactory);
    var response = service.sendUsageEventToAzureMarketplace(new UsageEvent());
    assertNotNull(response);
    verify(failedClient).submitUsageEvents(any(), any(), any(), any());
    verify(acceptedClient).submitUsageEvents(any(), any(), any(), any());
  }

  @Test
  void testSendUsageEventToMarketplaceFails() {
    Mockito.when(azureMarketplaceClientFactory.createClientForEachTenant())
        .thenReturn(List.of(failedClient, failedClient));
    var service =
        new AzureMarketplaceService(azureMarketplaceProperties, azureMarketplaceClientFactory);
    var usageEvent = new UsageEvent();
    assertThrows(
        AzureMarketplaceRequestFailedException.class,
        () -> service.sendUsageEventToAzureMarketplace(usageEvent));
  }

  /**
   * Test to reproduce <a href="https://issues.redhat.com/browse/SWATCH-2726">SWATCH-2726</a>.
   * According to the Azure API in <a
   * href="https://learn.microsoft.com/en-us/partner-center/marketplace-offers/marketplace-metering-service-apis">the
   * official documentation</a>, we might receive the following HTTP code in the response: - 200:
   * all good - 400: invalid request - 403: forbidden - 409: conflict, the usage event has already
   * been successfully reported Therefore, we should only retry another marketplace tenant when the
   * response is 403.
   */
  @Test
  void testStopSendingUsagesWhenMarketplaceRejectsTheRequest() throws ApiException {
    Mockito.when(azureMarketplaceClientFactory.createClientForEachTenant())
        .thenReturn(List.of(badRequestClient, acceptedClient));
    var service =
        new AzureMarketplaceService(azureMarketplaceProperties, azureMarketplaceClientFactory);
    service.sendUsageEventToAzureMarketplace(new UsageEvent());
    verify(acceptedClient, times(0)).submitUsageEvents(any(), any(), any(), any());
  }
}
