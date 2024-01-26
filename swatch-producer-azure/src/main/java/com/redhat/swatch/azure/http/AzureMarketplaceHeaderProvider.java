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
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

// https://learn.microsoft.com/en-us/partner-center/marketplace/partner-center-portal/pc-saas-registration#get-the-token-with-an-http-post
@Slf4j
public class AzureMarketplaceHeaderProvider implements ClientRequestFilter {

  private static final String AZURE_RESOURCE_GRANT = "resource";

  private OidcClients oidcClients;

  private OidcClient client;

  private Client clientInfo;

  private String tokenUrl;

  private String resourceGrantValue;

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
    this.resourceGrantValue = azureMarketplaceProperties.getOidcSaasMarketplaceResource();

    // set other fields before calling awaited method.
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
    var grantOptions = new HashMap<String, Map<String, String>>();
    grantOptions.put("client", Map.of(AZURE_RESOURCE_GRANT, resourceGrantValue));
    cfg.setGrantOptions(grantOptions);
    return oidcClients.newClient(cfg);
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("Authorization", "Bearer " + getAccessToken());
  }
}
