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

import com.redhat.swatch.azure.exception.AzureMarketplaceRequestFailedException;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.resources.ApiException;
import com.redhat.swatch.clients.azure.marketplace.api.resources.AzureMarketplaceApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AzureMarketplaceService {

  AzureMarketplaceProperties azureMarketplaceProperties;

  private List<AzureMarketplaceApi> marketplaceClients;

  @Inject
  public AzureMarketplaceService(
      AzureMarketplaceProperties azureMarketplaceProperties,
      AzureMarketplaceClientFactory azureMarketplaceClientFactory) {
    this.azureMarketplaceProperties = azureMarketplaceProperties;
    this.marketplaceClients = azureMarketplaceClientFactory.createClientForEachTenant();
  }

  public UsageEventOkResponse sendUsageEventToAzureMarketplace(UsageEvent usageEvent) {
    UsageEventOkResponse response = null;

    // Iterate through each set of credentials since we currently can not tell which is required.
    // Ignore those that fail.
    for (AzureMarketplaceApi api : marketplaceClients) {
      try {
        response =
            api.submitUsageEvents(
                azureMarketplaceProperties.getMarketplaceApiVersion(), usageEvent, null, null);
        break;
      } catch (ApiException | ProcessingException ex) {
        log.debug(
            "Exception occurred during azure marketplace api request, likely expected since credentials are tried at random: {}",
            ex);
      }
    }

    if (Objects.isNull(response)) {
      throw new AzureMarketplaceRequestFailedException();
    }
    return response;
  }
}
