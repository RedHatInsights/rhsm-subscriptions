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
package com.redhat.swatch.component.tests.api.hbi;

import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.logging.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Host state manager - orchestrates host seeding with templates and lifecycle tracking.
 *
 * <p>Provides entry points for different reporter types (QPC, RHSM, Satellite), applies templates,
 * tracks seeded hosts, and handles cleanup.
 *
 * <p>Usage:
 *
 * <pre>
 * HostStateManager hostManager = new HostStateManager(connector);
 *
 * // Create base host
 * QpcHost qpcHost = new QpcHost(orgId)
 *     .cores(4)
 *     .sockets(2);
 *
 * // Apply template and insert
 * SeededHost seeded = hostManager.qpc(qpcHost)
 *     .awsRhelLarge()
 *     .displayName("Custom")
 *     .insert();
 *
 * // Cleanup (or automatic in @AfterEach)
 * hostManager.cleanupAll();
 * </pre>
 */
public class HostStateManager {

  private final HostConnector connector;
  private final List<UUID> trackedHostIds = new ArrayList<>();

  public HostStateManager(HostConnector connector) {
    this.connector = Objects.requireNonNull(connector, "connector is required");
  }

  // ===== Entry Points for Different Reporter Types =====

  /**
   * Start building a host with pre-configured host.
   *
   * <p>Takes an existing Host and copies it, then applies template overrides.
   *
   * @param host the pre-configured host with NO fields already set
   * @return builder for applying template overrides
   */
  public HostBuilder createHost(Host host) {
    return new HostBuilder(this, host);
  }

  /**
   * Start building a host from scratch.
   *
   * <p>Creates a new Host with orgId and applies template defaults.
   *
   * @param orgId the organization ID
   * @return builder for applying template defaults
   */
  public HostBuilder createHost(String orgId) {
    return new HostBuilder(this, new Host(orgId));
  }

  // ===== QPC Reporter =====

  /**
   * Start building a QPC-reported host with pre-configured host.
   *
   * <p>Takes an existing Host and copies it, then applies QPC defaults for any unset fields.
   *
   * <p>QPC defaults applied (only if not already set):
   *
   * <ul>
   *   <li>reporter: "qpc"
   *   <li>reporters: ["qpc"]
   *   <li>arch: "x86_64"
   * </ul>
   *
   * @param host the pre-configured host with fields already set
   * @return builder for applying templates and additional overrides
   */
  public HostBuilder createQpcHost(Host host) {
    return new HostBuilder(this, new QpcHost(host));
  }

  /**
   * Start building a QPC-reported host from scratch.
   *
   * <p>Creates a new Host with orgId and applies all QPC defaults.
   *
   * <p>Defaults applied:
   *
   * <ul>
   *   <li>reporter: "qpc"
   *   <li>reporters: ["qpc"]
   *   <li>arch: "x86_64"
   * </ul>
   *
   * @param orgId the organization ID
   * @return builder for applying templates
   */
  public HostBuilder createQpcHost(String orgId) {
    return new HostBuilder(this, new QpcHost(orgId));
  }

  // ===== Satellite Reporter =====

  /**
   * Start building a Satellite-reported host with pre-configured host.
   *
   * <p>Takes an existing Host and copies it, then applies Satellite defaults for any unset fields.
   *
   * <p>Use this when you want to pre-configure fields that will be preserved through template
   * application:
   *
   * <pre>
   * Host preConfigured = new Host(orgId)
   *     .inventoryId("inv-123")
   *     .subscriptionManagerId("subman-456")
   *     .satelliteFact("virtual_host_uuid", "hypervisor-123");
   *
   * hostManager.satellite(preConfigured)
   *     .awsRhelLarge()
   *     .insert();
   * </pre>
   *
   * <p>Satellite defaults applied (only if not already set):
   *
   * <ul>
   *   <li>reporter: "satellite"
   *   <li>reporters: ["satellite"]
   *   <li>arch: "x86_64"
   *   <li>system_purpose_role: "Red Hat Enterprise Linux Server"
   *   <li>system_purpose_sla: "Premium"
   *   <li>system_purpose_usage: "Production"
   * </ul>
   *
   * <p>Satellite-specific facts from InventoryHost query:
   *
   * <ul>
   *   <li>h.facts->'satellite'->>'virtual_host_uuid' (hypervisor UUID)
   *   <li>h.facts->'satellite'->>'system_purpose_role'
   *   <li>h.facts->'satellite'->>'system_purpose_sla'
   *   <li>h.facts->'satellite'->>'system_purpose_usage'
   * </ul>
   *
   * @param host the pre-configured host with fields already set
   * @return builder for applying templates and additional overrides
   */
  public HostBuilder createSatelliteHost(Host host) {
    return new HostBuilder(this, new SatelliteHost(host));
  }

  /**
   * Start building a Satellite-reported host from scratch.
   *
   * <p>Creates a new Host with orgId and applies all Satellite defaults.
   *
   * <p>Use this for simple tests where you don't need to pre-configure fields:
   *
   * <pre>
   * hostManager.satellite(orgId)
   *     .physicalRhel4Socket()
   *     .displayName("Custom Name")
   *     .insert();
   * </pre>
   *
   * <p>Defaults applied:
   *
   * <ul>
   *   <li>reporter: "satellite"
   *   <li>reporters: ["satellite"]
   *   <li>arch: "x86_64"
   *   <li>system_purpose_role: "Red Hat Enterprise Linux Server"
   *   <li>system_purpose_sla: "Premium"
   *   <li>system_purpose_usage: "Production"
   * </ul>
   *
   * @param orgId the organization ID
   * @return builder for applying templates
   */
  public HostBuilder createSatelliteHost(String orgId) {
    return new HostBuilder(this, new SatelliteHost(orgId));
  }

  // ===== RHSM CONDUIT Reporter =====

  public HostBuilder createRhsmHost(Host host) {
    return new HostBuilder(this, new RhsmHost(host));
  }

  public HostBuilder createRhsmHost(String orgId) {
    return new HostBuilder(this, new RhsmHost(orgId));
  }

  // ===== Internal Seed Method (called by HostBuilder.insert()) =====

  SeededHost seed(Host host) {
    SeededHost seeded = connector.seed(host);
    trackedHostIds.add(seeded.hostId());
    Log.info(
        "Seeded host: %s (inventoryId=%s, orgId=%s)",
        seeded.hostId(), seeded.inventoryId(), seeded.orgId());
    return seeded;
  }

  // ===== Cleanup Methods =====

  /**
   * Delete all hosts tracked by this manager.
   *
   * <p>Call this in @AfterEach to ensure cleanup even on test failure.
   */
  public void cleanupAll() {
    List<UUID> toCleanup = new ArrayList<>(trackedHostIds);
    int successCount = 0;
    int failCount = 0;

    for (UUID hostId : toCleanup) {
      try {
        connector.cleanup(hostId);
        trackedHostIds.remove(hostId);
        successCount++;
      } catch (Exception e) {
        failCount++;
        Log.error("Failed to cleanup host %s: %s", hostId, e.getMessage());
      }
    }

    if (successCount > 0) {
      Log.info("Cleaned up %d host(s)", successCount);
    }
    if (failCount > 0) {
      Log.warn(
          "Failed to cleanup %d host(s). See Errors above for Host id's that are still remaining in the DB.",
          failCount);
    }
  }

  /**
   * Delete a specific host by ID.
   *
   * @param hostId the host UUID to delete
   */
  public void cleanup(UUID hostId) {
    try {
      connector.cleanup(hostId);
      trackedHostIds.remove(hostId);
      Log.info("Cleaned up host: %s", hostId);
    } catch (Exception e) {
      Log.error("Failed to cleanup host %s: %s", hostId, e.getMessage());
    }
  }

  /**
   * Check if a host exists in the database.
   *
   * @param hostId the host UUID to check
   * @return true if the host exists, false otherwise
   */
  public boolean hostExists(UUID hostId) {
    return connector.hostExists(hostId);
  }

  /**
   * Get the count of tracked hosts.
   *
   * @return number of hosts currently tracked
   */
  public int getTrackedCount() {
    return trackedHostIds.size();
  }
}
