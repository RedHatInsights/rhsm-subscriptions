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

import com.redhat.swatch.azure.file.AzureMarketplaceCredentials.Client;
import com.redhat.swatch.azure.file.AzureMarketplaceProperties;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClientConfig;
import io.quarkus.oidc.client.OidcClientConfig.Grant.Type;
import io.quarkus.oidc.client.OidcClients;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureMarketplaceHeaderProvider implements ClientRequestFilter {

  private OidcClients oidcClients;

  private OidcClient client;

  private Client clientInfo;

  private String tokenUrl;

  public AzureMarketplaceHeaderProvider(
      AzureMarketplaceProperties azureMarketplaceProperties,
      Client clientInfo,
      OidcClients oidcClients) {
    this.clientInfo = clientInfo;
    this.oidcClients = oidcClients;
    this.tokenUrl =
        azureMarketplaceProperties
            .getOauthTokenUrl()
            .replace("[TenantIdPlaceholder]", clientInfo.getTenantId());
    this.client = createOidcClient().await().indefinitely();
  }

  public String getAccessToken() {
    return client.getTokens().await().indefinitely().getAccessToken();
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
    try {
      requestContext.getHeaders().add("Authorization", "Bearer " + getAccessToken());
    } catch (IOException e) {
      log.error("Error getting OAuth access token", e);
      requestContext.abortWith(Response.serverError().build());
    }
  }
}
