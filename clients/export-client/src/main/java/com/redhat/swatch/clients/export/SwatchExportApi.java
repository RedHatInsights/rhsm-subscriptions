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
package com.redhat.swatch.clients.export;

import com.redhat.swatch.clients.BearerAuthFilter;
import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.ClientInfo;
import com.redhat.swatch.clients.ClientProxy;
import com.redhat.swatch.clients.TokenProvider;
import com.redhat.swatch.clients.export.api.client.ApiClient;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.client.auth.ApiKeyAuth;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import jakarta.ws.rs.client.Client;
import java.io.File;
import java.util.Objects;
import java.util.UUID;

public class SwatchExportApi extends ExportApi implements ClientInfo {

  private final ClientConfig config;
  private final TokenProvider tokenProvider;
  private final ClientProxy<ExportApi> proxy;

  public SwatchExportApi(ClientConfig config, TokenProvider tokenProvider) {
    this.config = Objects.requireNonNull(config, "config");
    this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    this.proxy = new ClientProxy<>(config, this::buildExportApi);
  }

  @Override
  public ClientConfig getConfig() {
    return config;
  }

  @Override
  public void downloadExportError(
      UUID id,
      String application,
      UUID resource,
      DownloadExportErrorRequest downloadExportErrorRequest)
      throws ApiException {
    invoke(api -> api.downloadExportError(id, application, resource, downloadExportErrorRequest));
  }

  @Override
  public void downloadExportUpload(UUID id, String application, UUID resource, File body)
      throws ApiException {
    invoke(api -> api.downloadExportUpload(id, application, resource, body));
  }

  private void invoke(ClientProxy.Call<ExportApi> call) throws ApiException {
    try {
      proxy.invoke(call);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(e);
    }
  }

  /**
   * Builds an {@link ExportApi} for {@code config}.
   *
   * <p>{@code authenticated=true} sends a Bearer token and omits PSK; {@code authenticated=false}
   * or unset keeps the {@code x-rh-exports-psk} flow.
   */
  private ExportApi buildExportApi(ClientConfig config) {
    ApiClient client = new ApiClient();
    client.setBasePath(config.getUri());
    Client httpClient = ClientProxy.buildHttpClient(config);
    client.setHttpClient(httpClient);

    if (config.isAuthenticated()) {
      httpClient.register(new BearerAuthFilter(tokenProvider));
    } else {
      String psk = config.getPsk();
      if (psk == null || psk.isBlank()) {
        throw new IllegalStateException(
            "Client '%s' missing 'psk' property".formatted(config.getName()));
      }

      ((ApiKeyAuth) client.getAuthentication("psk")).setApiKey(psk);
    }

    return new ExportApi(client);
  }
}
