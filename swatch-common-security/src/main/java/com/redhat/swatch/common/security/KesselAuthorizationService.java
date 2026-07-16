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
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;
import org.project_kessel.api.inventory.v1beta2.ReporterReference;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.SubjectReference;
import org.project_kessel.api.rbac.v2.Utils;

/**
 * gRPC client for Kessel permission checks.
 *
 * <p>Resilience features:
 *
 * <ul>
 *   <li>Configurable timeout on all gRPC calls (swatch.kessel.timeout-ms)
 *   <li>Automatic retry (3 attempts) on transient failures (UNAVAILABLE, DEADLINE_EXCEEDED, etc.)
 *   <li>Automatic channel recreation on UNAUTHENTICATED errors (token expiry)
 *   <li>Automatic channel recreation if channel enters SHUTDOWN state
 * </ul>
 */
@Slf4j
@ApplicationScoped
public class KesselAuthorizationService {

  private static final String RBAC_APP_NAME = "subscriptions";
  private static final String RBAC_ADMIN_PERMISSION = RBAC_APP_NAME + ":*:*";
  private static final String RBAC_READER_PERMISSION = RBAC_APP_NAME + ":reports:read";
  private static final String KESSEL_DOMAIN = "redhat";

  private static final Map<String, String> PERMISSION_TO_RELATION =
      Map.of(
          RBAC_ADMIN_PERMISSION, "subscriptions_report_view",
          RBAC_READER_PERMISSION, "subscriptions_report_view");

  private static final List<String> SWATCH_PERMISSIONS =
      List.of(RBAC_ADMIN_PERMISSION, RBAC_READER_PERMISSION);

  private static final List<Status.Code> TRANSIENT_FAILURE_CODES =
      List.of(
          Status.Code.UNAVAILABLE,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.ABORTED);

  private static final int MAX_RETRIES = 3;
  private static final long RETRY_DELAY_MS = 100;
  private static final long CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 30;

  @Inject KesselProperties properties;

  private volatile KesselInventoryServiceBlockingStub stub;
  private volatile ManagedChannel channel;

  @PostConstruct
  void init() {
    try {
      initializeChannel("startup");
    } catch (Exception e) {
      log.warn(
          "Failed to initialize Kessel authorization client; auth checks will deny by default", e);
    }
  }

  private synchronized void initializeChannel(String reason) {
    ManagedChannel oldChannel = channel;

    /*
     * OAuth2 authentication and TLS verification are currently disabled in Kessel, so the insecure
     * mode is the only option. TLS verification requires a CA cert which should be provided through
     * the Clowder config soon. When the CA cert is available, we'll have to update our code and use
     * it, then switch to the secure mode with OAuth2 and TLS.
     */
    Pair<KesselInventoryServiceBlockingStub, ManagedChannel> result;
    if (properties.insecure()) {
      log.warn(
          "Initializing insecure client for Kessel: OAuth2 authentication and TLS verification"
              + " will be disabled");
      result = new ClientBuilder(properties.endpoint()).insecure().build();
    } else {
      result = new ClientBuilder(properties.endpoint()).unauthenticated().build();
    }

    this.stub = result.getLeft();
    this.channel = result.getRight();
    log.info(
        "Kessel authorization client initialized: endpoint={} reason={}",
        properties.endpoint(),
        reason);

    if (oldChannel != null) {
      oldChannel.shutdown();
    }
  }

  private KesselInventoryServiceBlockingStub getClient() {
    if (channel == null) {
      return stub;
    }
    if (channel.getState(false) != ConnectivityState.SHUTDOWN) {
      return stub;
    }
    log.warn("Kessel gRPC channel is unhealthy, recreating");
    initializeChannel("unhealthy_channel");
    return stub;
  }

  @PreDestroy
  void shutdown() {
    if (channel == null) {
      return;
    }
    channel.shutdown();
    try {
      if (!channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        log.warn("Kessel gRPC channel did not terminate gracefully, forcing shutdown");
        channel.shutdownNow();
      }
    } catch (InterruptedException e) {
      channel.shutdownNow();
      Thread.currentThread().interrupt();
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
            .setSubject(
                SubjectReference.newBuilder()
                    .setResource(
                        ResourceReference.newBuilder()
                            .setResourceType("principal")
                            .setResourceId(subjectId.get())
                            .setReporter(ReporterReference.newBuilder().setType("rbac").build())
                            .build())
                    .build())
            .setRelation(relation)
            .setObject(Utils.workspaceResource("default"))
            .build();

    StatusRuntimeException lastException = null;
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        CheckResponse response =
            getClient()
                .withDeadlineAfter(properties.timeoutMs(), TimeUnit.MILLISECONDS)
                .check(request);
        boolean allowed = response.getAllowed() == Allowed.ALLOWED_TRUE;
        log.debug(
            "Kessel {} subject={}/{} relation={} on workspace=default (permission={})",
            allowed ? "allowed" : "denied",
            KESSEL_DOMAIN,
            subjectId.get(),
            relation,
            permission);
        return allowed;
      } catch (StatusRuntimeException e) {
        lastException = e;
        Status.Code code = e.getStatus().getCode();

        if (code == Status.Code.UNAUTHENTICATED) {
          log.warn(
              "Transient gRPC error from Kessel (attempt {}/{}): {} - {}. Recreating channel.",
              attempt + 1,
              MAX_RETRIES + 1,
              code,
              e.getMessage());
          initializeChannel("unauthenticated");
        } else if (TRANSIENT_FAILURE_CODES.contains(code) && attempt < MAX_RETRIES) {
          log.warn(
              "Transient gRPC error from Kessel (attempt {}/{}): {} - {}",
              attempt + 1,
              MAX_RETRIES + 1,
              code,
              e.getMessage());
          try {
            Thread.sleep(RETRY_DELAY_MS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        } else {
          log.warn(
              "Kessel check failed for subject={}/{}, permission={}: {}",
              KESSEL_DOMAIN,
              subjectId.get(),
              permission,
              e.getStatus());
          return false;
        }
      }
    }

    log.warn(
        "Kessel check exhausted retries for subject={}/{}, permission={}: {}",
        KESSEL_DOMAIN,
        subjectId.get(),
        permission,
        lastException != null ? lastException.getStatus() : "unknown");
    return false;
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
    log.debug(
        "Kessel permissions for orgId={}: granted={}", principal.getIdentity().getOrgId(), granted);
    return granted;
  }

  /**
   * Convert an RBACv1 permission string to a Kessel v2 relation name. The mapping follows the
   * rbac-config schema where v1 permissions map to v2 relations with singular resource names and
   * "view" instead of "read" (e.g. "subscriptions:reports:read" -> "subscriptions_report_view").
   */
  static String mapPermissionToRelation(String rbacPermission) {
    var relation = PERMISSION_TO_RELATION.get(rbacPermission);
    if (relation == null) {
      throw new IllegalArgumentException("Unknown RBACv1 permission: " + rbacPermission);
    }
    return relation;
  }

  // Visible for testing
  void setStub(KesselInventoryServiceBlockingStub stub) {
    this.stub = stub;
  }
}
