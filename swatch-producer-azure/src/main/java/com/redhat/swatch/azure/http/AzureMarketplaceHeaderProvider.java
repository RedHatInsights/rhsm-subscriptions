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
package com.redhat.swatch.azure.http;

import com.redhat.swatch.azure.file.AzureMarketplaceCredentials;
import com.redhat.swatch.azure.file.AzureMarketplaceCredentials.Client;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClientConfig;
import io.quarkus.oidc.client.OidcClientConfig.Grant.Type;
import io.quarkus.oidc.client.OidcClients;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

public class AzureMarketplaceHeaderProvider implements ClientRequestFilter {

  private OidcClients oidcClients;

  private Uni<OidcClient> clientUni;

  private AzureMarketplaceProperties azureMarketplaceProperties;

  private AzureMarketplaceCredentials azureMarketplaceCredentials;

  private Client clientInfo;

  private String tokenUrl;

  public AzureMarketplaceHeaderProvider(
      AzureMarketplaceProperties azureMarketplaceProperties,
      AzureMarketplaceCredentials azureMarketplaceCredentials,
      Client clientInfo,
      OidcClients oidcClients) {
    this.azureMarketplaceCredentials = azureMarketplaceCredentials;
    this.clientInfo = clientInfo;
    this.oidcClients = oidcClients;
    this.tokenUrl =
        azureMarketplaceProperties
            .getOauthTokenUrl()
            .replace("[TenantIdPlaceholder]", clientInfo.getTenantId());
    this.clientUni = createOidcClient();
  }

  public String getAccessToken() {
    return clientUni.await().indefinitely().getTokens().await().indefinitely().getAccessToken();
  }

  private Uni<OidcClient> createOidcClient() {
    OidcClientConfig cfg = new OidcClientConfig();
    cfg.setId(clientInfo.getTenantId());
    cfg.setTokenPath(tokenUrl);
    cfg.setClientId(clientInfo.getClientId());
    cfg.getGrant().setType(Type.CLIENT);
    cfg.getCredentials().setSecret(clientInfo.getClientSecret());
    return oidcClients.newClient(cfg);
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("Authorization", "Bearer " + getAccessToken());
  }
}
