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
package com.redhat.swatch.clients.rbac;

import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.ClientInfo;
import com.redhat.swatch.clients.ClientProxy;
import com.redhat.swatch.clients.rbac.api.client.ApiClient;
import com.redhat.swatch.clients.rbac.api.client.ApiException;
import com.redhat.swatch.clients.rbac.api.model.Access;
import com.redhat.swatch.clients.rbac.api.model.AccessPagination;
import com.redhat.swatch.clients.rbac.api.resources.AccessApi;
import java.util.List;
import java.util.Objects;

public class SwatchRbacApi implements RbacApi, ClientInfo {

  private final ClientConfig config;
  private final ClientProxy<AccessApi> proxy;

  public SwatchRbacApi(ClientConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    this.proxy = new ClientProxy<>(config, SwatchRbacApi::buildApi);
  }

  @Override
  public ClientConfig getConfig() {
    return config;
  }

  @Override
  public List<Access> getCurrentUserAccess(String applicationName) throws RbacApiException {
    return getAccess(applicationName, null);
  }

  @Override
  public List<Access> getCurrentIdentityAccess(String applicationName, String identity)
      throws RbacApiException {
    return getAccess(applicationName, identity);
  }

  private List<Access> getAccess(String applicationName, String identity) throws RbacApiException {
    try {
      AccessPagination response =
          proxy.client().getPrincipalAccess(applicationName, null, identity, null, null);
      return response == null ? List.of() : response.getData();
    } catch (ApiException e) {
      throw new RbacApiException("Unable to get current access.", e);
    }
  }

  private static AccessApi buildApi(ClientConfig config) {
    ApiClient client = new ApiClient();
    client.setBasePath(config.getUri());
    client.setHttpClient(ClientProxy.buildHttpClient(config));
    return new AccessApi(client);
  }
}
