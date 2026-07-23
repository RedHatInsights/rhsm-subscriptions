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

import com.redhat.swatch.kessel.KesselAuthorizationClient;
import com.redhat.swatch.kessel.KesselConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2AuthRequest;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;
import org.project_kessel.api.rbac.v2.FetchWorkspace;

/**
 * CDI wrapper around the shared {@link KesselAuthorizationClient}. Handles Quarkus-specific
 * concerns: CDI lifecycle, RBAC OAuth2 authentication for workspace lookups, and principal ID
 * resolution from {@link RhIdentityPrincipal}.
 */
@Slf4j
@ApplicationScoped
public class KesselAuthorizationService {

  @Inject KesselProperties properties;

  private KesselAuthorizationClient client;
  private volatile OAuth2AuthRequest rbacAuth;
  private final ConcurrentHashMap<String, String> workspaceCache = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    KesselConfig config =
        new KesselConfig() {
          @Override
          public String endpoint() {
            return properties.endpoint();
          }

          @Override
          public boolean insecure() {
            return properties.insecure();
          }

          @Override
          public long timeoutMs() {
            return properties.timeoutMs();
          }
        };

    client = new KesselAuthorizationClient(config, this::getDefaultWorkspaceId);
    client.init();

    try {
      initializeRbacAuth();
    } catch (Exception e) {
      log.warn(
          "Failed to initialize RBAC OAuth2 client; workspace lookups will be unauthenticated", e);
    }
  }

  private void initializeRbacAuth() throws Exception {
    var issuerUrl = properties.authDiscoveryIssuerUrl().filter(s -> !s.isBlank());
    var clientId = properties.authClientId().filter(s -> !s.isBlank());
    var clientSecret = properties.authClientSecret().filter(s -> !s.isBlank());
    if (issuerUrl.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
      log.info("RBAC OAuth2 credentials not configured; workspace fetches will be unauthenticated");
      return;
    }
    var discovery = OIDCDiscovery.fetchOIDCDiscovery(issuerUrl.get());
    var credentials =
        new OAuth2ClientCredentials(
            new ClientConfigAuth(clientId.get(), clientSecret.get(), discovery.tokenEndpoint()));
    this.rbacAuth = new OAuth2AuthRequest(credentials);
    log.info("RBAC OAuth2 client initialized for workspace lookups");
  }

  @PreDestroy
  void shutdown() {
    if (client != null) {
      client.shutdown();
    }
  }

  public boolean checkAccess(RhIdentityPrincipal principal, String permission) {
    var subjectId = KesselPrincipalIds.fromRhIdentityPrincipal(principal);
    if (subjectId.isEmpty()) {
      log.warn(
          "Cannot determine Kessel principal id for identity type={} orgId={}; denying access",
          principal.getIdentity().getType(),
          principal.getIdentity().getOrgId());
      return false;
    }
    return client.checkAccess(subjectId.get(), permission, principal.getIdentity().getOrgId());
  }

  public List<String> getPermissions(RhIdentityPrincipal principal) {
    var subjectId = KesselPrincipalIds.fromRhIdentityPrincipal(principal);
    if (subjectId.isEmpty()) {
      log.warn(
          "Cannot determine Kessel principal id for identity type={} orgId={}; denying access",
          principal.getIdentity().getType(),
          principal.getIdentity().getOrgId());
      return List.of();
    }
    var granted = client.getPermissions(subjectId.get(), principal.getIdentity().getOrgId());
    log.debug(
        "Kessel permissions for orgId={}: granted={}", principal.getIdentity().getOrgId(), granted);
    return granted;
  }

  private String getDefaultWorkspaceId(String orgId) {
    return workspaceCache.computeIfAbsent(orgId, this::fetchDefaultWorkspaceId);
  }

  private String fetchDefaultWorkspaceId(String orgId) {
    try {
      var workspace =
          FetchWorkspace.fetchDefaultWorkspace(properties.rbacBaseEndpoint(), orgId, rbacAuth);
      log.info("Fetched default workspace for orgId={}: id={}", orgId, workspace.getId());
      return workspace.getId();
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch default workspace for orgId=" + orgId, e);
    }
  }

  // Visible for testing
  void setStub(KesselInventoryServiceBlockingStub stub) {
    if (client == null) {
      KesselConfig config =
          new KesselConfig() {
            @Override
            public String endpoint() {
              return properties.endpoint();
            }

            @Override
            public boolean insecure() {
              return properties.insecure();
            }

            @Override
            public long timeoutMs() {
              return properties.timeoutMs();
            }
          };
      client = new KesselAuthorizationClient(config, this::getDefaultWorkspaceId);
    }
    client.setStub(stub);
  }

  // Visible for testing
  void setWorkspaceId(String orgId, String workspaceId) {
    workspaceCache.put(orgId, workspaceId);
  }
}
