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
package com.redhat.swatch.common.security;

import com.nimbusds.jose.util.Pair;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;
import org.project_kessel.api.rbac.v2.Utils;

@Slf4j
@ApplicationScoped
public class KesselAuthorizationService {

  private static final String RBAC_APP_NAME = "subscriptions";
  private static final String RBAC_ADMIN_PERMISSION = RBAC_APP_NAME + ":*:*";
  private static final String RBAC_READER_PERMISSION = RBAC_APP_NAME + ":reports:read";
  private static final String KESSEL_DOMAIN = "redhat";

  private static final List<String> SWATCH_PERMISSIONS =
      List.of(RBAC_ADMIN_PERMISSION, RBAC_READER_PERMISSION);

  @Inject KesselProperties properties;

  private KesselInventoryServiceBlockingStub stub;
  private ManagedChannel channel;

  @PostConstruct
  void init() {
    try {
      var builder = new ClientBuilder(properties.endpoint());

      if (properties.insecure()) {
        builder.insecure();
      } else if (properties.authEnabled()) {
        var issuer =
            properties
                .oidcIssuer()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "swatch.kessel.oidc-issuer is required when auth is enabled"));
        var clientId =
            properties
                .clientId()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "swatch.kessel.client-id is required when auth is enabled"));
        var clientSecret =
            properties
                .clientSecret()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "swatch.kessel.client-secret is required when auth is enabled"));

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
      log.info("Kessel authorization client initialized: endpoint={}", properties.endpoint());
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

  /**
   * Check if the given principal has the specified RBACv1-style permission via Kessel.
   *
   * @param principal the authenticated identity
   * @param permission RBACv1 permission string (e.g. "subscriptions:*:*")
   * @return true if access is allowed
   */
  public boolean checkAccess(RhIdentityPrincipal principal, String permission) {
    if (stub == null) {
      log.warn("Kessel client not initialized; denying access");
      return false;
    }
    var subjectId = KesselPrincipalIds.fromRhIdentityPrincipal(principal);
    if (subjectId.isEmpty()) {
      log.warn(
          "Cannot determine Kessel principal id for identity type={} orgId={}; denying access",
          principal.getIdentity().getType(),
          principal.getIdentity().getOrgId());
      return false;
    }
    var relation = mapPermissionToRelation(permission);

    var request =
        CheckRequest.newBuilder()
            .setSubject(Utils.principalSubject(subjectId.get(), KESSEL_DOMAIN))
            .setRelation(relation)
            .setObject(Utils.workspaceResource("default"))
            .build();

    try {
      CheckResponse response = stub.check(request);
      return response.getAllowed() == Allowed.ALLOWED_TRUE;
    } catch (StatusRuntimeException e) {
      log.warn(
          "Kessel check failed for subject={}/{}, permission={}: {}",
          KESSEL_DOMAIN,
          subjectId.get(),
          permission,
          e.getStatus());
      return false;
    }
  }

  /**
   * Get all granted RBACv1-style permissions for the given principal via Kessel. This method checks
   * each known SWATCH permission and returns the ones that are allowed — matching the interface
   * used by RbacRolesAugmentor.
   */
  public List<String> getPermissions(RhIdentityPrincipal principal) {
    var granted = new ArrayList<String>();
    for (var permission : SWATCH_PERMISSIONS) {
      if (checkAccess(principal, permission)) {
        granted.add(permission);
      }
    }
    return granted;
  }

  /**
   * Convert an RBACv1 permission string to a Kessel relation name. Colons become underscores, and
   * wildcards become "all".
   */
  static String mapPermissionToRelation(String rbacPermission) {
    return rbacPermission.replace(':', '_').replace("*", "all");
  }

  // Visible for testing
  void setStub(KesselInventoryServiceBlockingStub stub) {
    this.stub = stub;
  }
}
