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
 * Host state manager - orchestrates host seeding and lifecycle tracking.
 *
 * <p>There is a single kind of HBI host; a {@link HostBuilder} always builds one {@link Host}. What
 * varies is which reporters contributed facts to it - attach those via {@link
 * HostBuilder#rhsmFacts}, {@link HostBuilder#satelliteFacts}, and {@link HostBuilder#qpcFacts}.
 *
 * <p>Usage:
 *
 * <pre>
 * HostStateManager hostManager = new HostStateManager(connector);
 *
 * SeededHost seeded = hostManager.createHost(orgId)
 *     .displayName("Custom")
 *     .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
 *     .systemProfileFacts(SystemProfileFacts.builder().numberOfSockets(2).build())
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

  // ===== Entry Points =====

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

  // ===== Internal Methods (called by HostBuilder.insert()/update()) =====

  SeededHost seed(Host host) {
    SeededHost seeded = connector.seed(host);
    trackedHostIds.add(seeded.hostId());
    Log.info(
        "Seeded host: %s (inventoryId=%s, orgId=%s)",
        seeded.hostId(), seeded.inventoryId(), seeded.orgId());
    return seeded;
  }

  /**
   * Update a previously-seeded host. Unlike {@link #seed}, does not add to {@link #trackedHostIds}
   * - the host is already tracked from its original {@code insert()}.
   */
  SeededHost update(Host host) {
    SeededHost updated = connector.update(host);
    Log.info(
        "Updated host: %s (inventoryId=%s, orgId=%s)",
        updated.hostId(), updated.inventoryId(), updated.orgId());
    return updated;
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
