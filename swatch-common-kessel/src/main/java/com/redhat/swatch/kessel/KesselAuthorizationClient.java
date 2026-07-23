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
package com.redhat.swatch.kessel;

import com.nimbusds.jose.util.Pair;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
 * Framework-neutral gRPC client for Kessel permission checks.
 *
 * <p>Resilience features:
 *
 * <ul>
 *   <li>Configurable timeout on all gRPC calls
 *   <li>Automatic retry (up to 3 attempts) on transient failures (UNAVAILABLE, DEADLINE_EXCEEDED,
 *       etc.)
 *   <li>Automatic channel recreation on UNAUTHENTICATED errors (token expiry)
 *   <li>Automatic channel recreation if channel enters SHUTDOWN state
 * </ul>
 */
@Slf4j
public class KesselAuthorizationClient {

  static final String RBAC_APP_NAME = "subscriptions";
  static final String RBAC_ADMIN_PERMISSION = RBAC_APP_NAME + ":*:*";
  static final String RBAC_READER_PERMISSION = RBAC_APP_NAME + ":reports:read";
  static final String KESSEL_DOMAIN = "redhat";

  static final Map<String, String> PERMISSION_TO_RELATION =
      Map.of(
          RBAC_ADMIN_PERMISSION, "subscriptions_report_view",
          RBAC_READER_PERMISSION, "subscriptions_report_view");

  static final List<Status.Code> TRANSIENT_FAILURE_CODES =
      List.of(
          Status.Code.UNAVAILABLE,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.ABORTED);

  static final int MAX_RETRIES = 3;
  static final long RETRY_DELAY_MS = 100;
  static final long CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 30;

  private final KesselConfig config;
  private final WorkspaceResolver workspaceResolver;
  private volatile KesselInventoryServiceBlockingStub stub;
  private volatile ManagedChannel channel;

  public KesselAuthorizationClient(KesselConfig config, WorkspaceResolver workspaceResolver) {
    this.config = config;
    this.workspaceResolver = workspaceResolver;
  }

  public void init() {
    try {
      initializeChannel("startup", null);
    } catch (Exception e) {
      log.warn(
          "Failed to initialize Kessel authorization client; auth checks will deny by default", e);
    }
  }

  synchronized void initializeChannel(String reason, ManagedChannel brokenChannel) {
    if (this.channel != null && this.channel != brokenChannel) {
      return;
    }
    ManagedChannel oldChannel = this.channel;

    Pair<KesselInventoryServiceBlockingStub, ManagedChannel> result;
    if (config.insecure()) {
      log.warn(
          "Initializing insecure client for Kessel: OAuth2 authentication and TLS verification"
              + " will be disabled");
      result = new ClientBuilder(config.endpoint()).insecure().build();
    } else {
      result = new ClientBuilder(config.endpoint()).unauthenticated().build();
    }

    this.stub = result.getLeft();
    this.channel = result.getRight();
    log.info(
        "Kessel authorization client initialized: endpoint={} reason={}",
        config.endpoint(),
        reason);

    if (oldChannel != null) {
      oldChannel.shutdown();
    }
  }

  KesselInventoryServiceBlockingStub getClient() {
    ManagedChannel currentChannel = channel;
    if (currentChannel == null) {
      return stub;
    }
    if (currentChannel.getState(false) != ConnectivityState.SHUTDOWN) {
      return stub;
    }
    log.warn("Kessel gRPC channel is unhealthy, recreating");
    initializeChannel("unhealthy_channel", currentChannel);
    return stub;
  }

  public void shutdown() {
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
   * Check if the given subject has the specified RBACv1-style permission via Kessel.
   *
   * @param subjectId the Kessel principal identifier (e.g. user_id)
   * @param permission RBACv1 permission string (e.g. "subscriptions:reports:read")
   * @param orgId the organization ID, used to resolve the workspace
   * @return true if access is allowed
   */
  public boolean checkAccess(String subjectId, String permission, String orgId) {
    if (stub == null) {
      log.warn("Kessel client not initialized; denying access for subject={}", subjectId);
      return false;
    }
    var relation = mapPermissionToRelation(permission);
    var workspaceId = workspaceResolver.getDefaultWorkspaceId(orgId);

    var request =
        CheckRequest.newBuilder()
            .setSubject(
                SubjectReference.newBuilder()
                    .setResource(
                        ResourceReference.newBuilder()
                            .setResourceType("principal")
                            .setResourceId(subjectId)
                            .setReporter(ReporterReference.newBuilder().setType("rbac").build())
                            .build())
                    .build())
            .setRelation(relation)
            .setObject(Utils.workspaceResource(workspaceId))
            .build();

    StatusRuntimeException lastException = null;
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        ManagedChannel currentChannel = channel;
        CheckResponse response =
            getClient().withDeadlineAfter(config.timeoutMs(), TimeUnit.MILLISECONDS).check(request);
        boolean allowed = response.getAllowed() == Allowed.ALLOWED_TRUE;
        log.debug(
            "Kessel {} subject={}/{} relation={} on workspace={} (permission={})",
            allowed ? "allowed" : "denied",
            KESSEL_DOMAIN,
            subjectId,
            relation,
            workspaceId,
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
          initializeChannel("unauthenticated", channel);
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
              subjectId,
              permission,
              e.getStatus());
          return false;
        }
      }
    }

    log.warn(
        "Kessel check exhausted retries for subject={}/{}, permission={}: {}",
        KESSEL_DOMAIN,
        subjectId,
        permission,
        lastException != null ? lastException.getStatus() : "unknown");
    return false;
  }

  /**
   * Get granted RBACv1-style permissions for the given subject via Kessel.
   *
   * <p>Currently both admin and reader permissions map to the same Kessel relation, so a single
   * Check call is made. Only the reader permission is emitted until distinct Kessel relations
   * exist.
   */
  public List<String> getPermissions(String subjectId, String orgId) {
    if (subjectId == null || subjectId.isBlank()) {
      log.warn("Missing Kessel subject id; denying access");
      return List.of();
    }
    if (checkAccess(subjectId, RBAC_READER_PERMISSION, orgId)) {
      return List.of(RBAC_READER_PERMISSION);
    }
    return List.of();
  }

  /**
   * Convert an RBACv1 permission string to a Kessel v2 relation name. The mapping follows the
   * rbac-config schema where v1 permissions map to v2 relations with singular resource names and
   * "view" instead of "read" (e.g. "subscriptions:reports:read" -> "subscriptions_report_view").
   */
  public static String mapPermissionToRelation(String rbacPermission) {
    var relation = PERMISSION_TO_RELATION.get(rbacPermission);
    if (relation == null) {
      throw new IllegalArgumentException("Unknown RBACv1 permission: " + rbacPermission);
    }
    return relation;
  }

  // Visible for testing
  public void setStub(KesselInventoryServiceBlockingStub stub) {
    this.stub = stub;
  }
}
