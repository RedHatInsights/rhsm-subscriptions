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
package org.candlepin.subscriptions.rbac;

import com.redhat.swatch.kessel.HccCredentials;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.project_kessel.api.auth.AuthRequest;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.rbac.v2.FetchWorkspace;

/** The SDK workspace client uses the same endpoint trust and workload credentials as RBAC v1. */
public class RbacWorkspaceClient implements AutoCloseable {
  private final KesselProperties kessel;
  private final RbacProperties rbac;
  private final HccCredentials credentials;
  private final HttpClient client;

  public RbacWorkspaceClient(
      KesselProperties kessel, RbacProperties rbac, HccCredentials credentials) {
    this.kessel = kessel;
    this.rbac = rbac;
    this.credentials = credentials;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(kessel.getTimeoutMs()))
            .sslContext(org.candlepin.subscriptions.http.HttpClient.getSslContext(rbac))
            .build();
  }

  public String defaultWorkspaceId(String orgId) {
    AuthRequest auth =
        rbac.isAuthenticated() || kessel.isAuthEnabled() || credentials.isConfigured()
            ? builder -> builder.setHeader("Authorization", credentials.authorizationHeader())
            : null;
    try {
      String endpoint = kessel.getRbacBaseEndpoint();
      if (endpoint == null || endpoint.isBlank()) {
        endpoint = rbac.getUrl().replaceFirst("/api/rbac/v1/?$", "");
      }
      return FetchWorkspace.fetchDefaultWorkspace(endpoint, orgId, auth, client).getId();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("RBAC workspace lookup was interrupted", e);
    } catch (IOException | OAuth2Exception e) {
      throw new IllegalStateException("RBAC workspace lookup failed", e);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
