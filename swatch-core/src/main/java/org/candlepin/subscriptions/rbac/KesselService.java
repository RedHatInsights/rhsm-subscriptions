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

import com.nimbusds.jose.util.Pair;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;
import org.project_kessel.api.rbac.v2.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KesselService {

  private static final Logger log = LoggerFactory.getLogger(KesselService.class);

  private static final String RBAC_APP_NAME = "subscriptions";
  private static final String RBAC_ADMIN_PERMISSION = RBAC_APP_NAME + ":*:*";
  private static final String RBAC_READER_PERMISSION = RBAC_APP_NAME + ":reports:read";
  private static final String KESSEL_DOMAIN = "redhat";

  private static final List<String> SWATCH_PERMISSIONS =
      List.of(RBAC_ADMIN_PERMISSION, RBAC_READER_PERMISSION);

  private final KesselProperties properties;
  private KesselInventoryServiceBlockingStub stub;
  private ManagedChannel channel;

  public KesselService(KesselProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void init() {
    try {
      var builder = new ClientBuilder(properties.getEndpoint());

      if (properties.isInsecure()) {
        builder.insecure();
      } else if (properties.isAuthEnabled()) {
        var issuer = properties.getOidcIssuer();
        var clientId = properties.getClientId();
        var clientSecret = properties.getClientSecret();

        if (issuer == null || clientId == null || clientSecret == null) {
          throw new IllegalStateException(
              "Kessel oidcIssuer, clientId, and clientSecret are required when auth is enabled");
        }

        var discovery = OIDCDiscovery.fetchOIDCDiscovery(issuer);
        var authConfig = new ClientConfigAuth(clientId, clientSecret, discovery.tokenEndpoint());
        var oauthClient = new OAuth2ClientCredentials(authConfig);
        builder.oauth2ClientAuthenticated(oauthClient);
      } else {
        builder.unauthenticated();
      }

      Pair<KesselInventoryServiceBlockingStub, ManagedChannel> result = builder.build();
      this.stub = result.getLeft();
      this.channel = result.getRight();
      log.info("Kessel authorization client initialized: endpoint={}", properties.getEndpoint());
    } catch (Exception e) {
      log.warn(
          "Failed to initialize Kessel authorization client; auth checks will deny by default", e);
    }
  }

  @PreDestroy
  void shutdown() {
    if (channel != null) {
      try {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        channel.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  public boolean checkAccess(String subjectId, String permission) {
    if (stub == null) {
      log.warn("Kessel client not initialized; denying access for subject={}", subjectId);
      return false;
    }
    var relation = mapPermissionToRelation(permission);

    var request =
        CheckRequest.newBuilder()
            .setSubject(Utils.principalSubject(subjectId, KESSEL_DOMAIN))
            .setRelation(relation)
            .setObject(Utils.workspaceResource("default"))
            .build();

    try {
      CheckResponse response = stub.check(request);
      return response.getAllowed() == Allowed.ALLOWED_TRUE;
    } catch (StatusRuntimeException e) {
      log.warn(
          "Kessel check failed for subject={}, permission={}: {}",
          subjectId,
          permission,
          e.getStatus());
      return false;
    }
  }

  public List<String> getPermissions(String subjectId) {
    if (subjectId == null || subjectId.isBlank()) {
      log.warn("Missing Kessel subject id; denying access");
      return List.of();
    }
    var granted = new ArrayList<String>();
    for (var permission : SWATCH_PERMISSIONS) {
      if (checkAccess(subjectId, permission)) {
        granted.add(permission);
      }
    }
    return granted;
  }

  static String mapPermissionToRelation(String rbacPermission) {
    return rbacPermission.replace(':', '_').replace("*", "all");
  }

  // Visible for testing
  void setStub(KesselInventoryServiceBlockingStub stub) {
    this.stub = stub;
  }
}
