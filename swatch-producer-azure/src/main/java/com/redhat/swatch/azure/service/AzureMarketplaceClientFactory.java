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

import com.redhat.swatch.azure.file.AzureMarketplaceCredentials;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import com.redhat.swatch.azure.http.AzureMarketplaceHeaderProvider;
import com.redhat.swatch.azure.service.model.AzureClient;
import com.redhat.swatch.clients.azure.marketplace.api.resources.AzureMarketplaceApi;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AzureMarketplaceClientFactory {

  AzureMarketplaceProperties azureMarketplaceProperties;
  AzureMarketplaceCredentials azureMarketplaceCredentials;
  private OidcClients oidcClients;

  @Inject
  public AzureMarketplaceClientFactory(
      AzureMarketplaceProperties azureMarketplaceProperties,
      AzureMarketplaceCredentials azureMarketplaceCredentials,
      OidcClients oidcClients) {
    this.azureMarketplaceProperties = azureMarketplaceProperties;
    this.azureMarketplaceCredentials = azureMarketplaceCredentials;
    this.oidcClients = oidcClients;
  }

  public List<AzureClient> createClientForEachTenant() {
    return azureMarketplaceCredentials.getClients().stream()
        .map(
            client -> {
              try {
                AzureMarketplaceApi api;
                if (azureMarketplaceProperties.isDisableAzureOidc()) {
                  api =
                      QuarkusRestClientBuilder.newBuilder()
                          .baseUri(new URI(azureMarketplaceProperties.getMarketplaceBaseUrl()))
                          .build(AzureMarketplaceApi.class);
                } else {
                  api =
                      QuarkusRestClientBuilder.newBuilder()
                          .baseUri(new URI(azureMarketplaceProperties.getMarketplaceBaseUrl()))
                          .register(
                              new AzureMarketplaceHeaderProvider(
                                  azureMarketplaceProperties, client, oidcClients))
                          .build(AzureMarketplaceApi.class);
                }
                return new AzureClient(client.getClientId(), api);
              } catch (URISyntaxException ex) {
                log.error("Unable to create URI for Azure authentication for client.", ex);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
