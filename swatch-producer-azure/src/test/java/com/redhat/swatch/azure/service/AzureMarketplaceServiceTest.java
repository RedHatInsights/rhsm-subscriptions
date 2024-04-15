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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.azure.marketplace.api.resources.AzureMarketplaceApi;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AzureMarketplaceServiceTest {

  AzureMarketplaceProperties azureMarketplaceProperties = new AzureMarketplaceProperties();

  AzureMarketplaceClientFactory azureMarketplaceClientFactory;
  AzureMarketplaceApi acceptedClient;
  AzureMarketplaceApi failedClient;

  @BeforeEach
  void setup() throws Exception {
    acceptedClient = mock(AzureMarketplaceApi.class);
    failedClient = mock(AzureMarketplaceApi.class);
    azureMarketplaceClientFactory = Mockito.mock(AzureMarketplaceClientFactory.class);
    Mockito.when(acceptedClient.submitUsageEvents(any(), any(), any(), any()))
        .thenReturn(new UsageEventOkResponse().status(UsageEventStatusEnum.ACCEPTED));
    Mockito.when(failedClient.submitUsageEvents(any(), any(), any(), any()))
        .thenThrow(new ProcessingException("error"));
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
}
