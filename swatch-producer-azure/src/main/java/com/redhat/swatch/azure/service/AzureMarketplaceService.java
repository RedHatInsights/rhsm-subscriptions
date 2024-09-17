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
import com.redhat.swatch.azure.service.model.AzureClient;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEvent;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.azure.marketplace.api.resources.ApiException;
import com.redhat.swatch.clients.azure.marketplace.api.resources.AzureMarketplaceApi;
import io.micrometer.common.util.StringUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AzureMarketplaceService {

  private static final int HTTP_STATUS_CONFLICT = 409;
  private static final int HTTP_STATUS_BAD_REQUEST = 400;

  private final AzureMarketplaceProperties azureMarketplaceProperties;
  private final List<AzureClient> marketplaceClients;

  @Inject
  public AzureMarketplaceService(
      AzureMarketplaceProperties azureMarketplaceProperties,
      AzureMarketplaceClientFactory azureMarketplaceClientFactory) {
    this.azureMarketplaceProperties = azureMarketplaceProperties;
    this.marketplaceClients = azureMarketplaceClientFactory.createClientForEachTenant();
  }

  public UsageEventOkResponse sendUsageEventToAzureMarketplace(UsageEvent usageEvent) {
    // try to find a matching client using the usage event client ID
    var client = findAzureClient(usageEvent.getClientId());
    if (client.isPresent()) {
      return sendEventToAzureMarketplace(usageEvent, client.get().api());
    }

    // Iterate through each set of credentials since we currently can not tell which is required.
    // Ignore those that fail.
    for (AzureClient azureClient : marketplaceClients) {
      var response = tryToSendEventToAzureMarketplace(usageEvent, azureClient.api());
      if (response.isPresent()) {
        return response.get();
      }
    }

    throw new AzureMarketplaceRequestFailedException();
  }

  private Optional<AzureClient> findAzureClient(String clientId) {
    if (StringUtils.isEmpty(clientId)) {
      return Optional.empty();
    }

    var azureClient =
        marketplaceClients.stream().filter(c -> clientId.equals(c.clientId())).findFirst();
    if (azureClient.isEmpty()) {
      log.warn(
          "The azure client ID '{}' was not found. It will iterate over all the existing clients.",
          clientId);
    }

    return azureClient;
  }

  private UsageEventOkResponse sendEventToAzureMarketplace(
      UsageEvent usageEvent, AzureMarketplaceApi api) {
    return tryToSendEventToAzureMarketplace(usageEvent, api)
        .orElseThrow(AzureMarketplaceRequestFailedException::new);
  }

  private Optional<UsageEventOkResponse> tryToSendEventToAzureMarketplace(
      UsageEvent usageEvent, AzureMarketplaceApi api) {
    UsageEventOkResponse response = null;
    try {
      response =
          api.submitUsageEvents(
              azureMarketplaceProperties.getMarketplaceApiVersion(), usageEvent, null, null);
    } catch (ApiException ex) {
      int status = ex.getResponse().getStatus();
      if (HTTP_STATUS_CONFLICT == status || HTTP_STATUS_BAD_REQUEST == status) {
        // don't try another tenant if the client returned a known error code.
        response =
            new UsageEventOkResponse()
                .status(
                    HTTP_STATUS_CONFLICT == status
                        ? UsageEventStatusEnum.DUPLICATE
                        : UsageEventStatusEnum.ERROR);
      } else {
        log.debug(
            "Exception occurred during azure marketplace api request with HTTP status '{}', likely expected since credentials are tried at random",
            status,
            ex);
      }
    } catch (ProcessingException ex) {
      log.error("Exception occurred during azure marketplace api request", ex);
    }

    return Optional.ofNullable(response);
  }
}
