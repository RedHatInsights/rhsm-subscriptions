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
package utils;

import com.redhat.swatch.component.tests.api.db.DatabaseService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HBI database seeder for nightly tally component tests.
 *
 * <p>Inserts hosts into the HBI (Host-Based Inventory) database schema and tracks them for cleanup
 * (rollback on test failure).
 *
 * <p>Usage:
 *
 * <pre>
 * // In test class:
 * TallyHbiDbSeeder hbiSeeder = new TallyHbiDbSeeder(database);
 *
 * // In test method:
 * SeededHost host = hbiSeeder.insertRhelHost(orgId, inventoryId, subManId, displayName, cores, sockets);
 *
 * // In @AfterEach cleanup:
 * hbiSeeder.deleteAllInsertedHosts();
 * </pre>
 */
public final class TallyHbiDbSeeder {

  private final DatabaseService hbiDatabase;

  // Default values for test hosts
  private static final String DEFAULT_INVENTORY_ID = "test-inventory-id";
  private static final String DEFAULT_SUBMAN_ID = "test-subman-id";
  private static final String DEFAULT_DISPLAY_NAME = "Test RHEL Host";
  private static final String DEFAULT_CLOUD_DISPLAY_NAME = "Test Cloud Host";
  private static final String DEFAULT_PROVIDER_ID = "i-test-instance-123";
  private static final int DEFAULT_CORES = 4;
  private static final int DEFAULT_SOCKETS = 2;
  private static final String RHEL_PRODUCT_ID = "69"; // RHEL product ID

  private final List<UUID> insertedHostIds = new ArrayList<>();
  // Socket increase mapping for RHEL physical hosts
  // Maps actual socket count -> reported socket count for tally
  private static final java.util.Map<Integer, Integer> RHEL_PER_SOCKET_INCREASE =
      java.util.Map.of(1, 2, 2, 2, 4, 4, 7, 8);

  /**
   * Create HBI database seeder.
   *
   * @param hbiDatabase the HBI database service (injected via @HbiDatabase annotation)
   */
  public TallyHbiDbSeeder(DatabaseService hbiDatabase) {
    this.hbiDatabase = Objects.requireNonNull(hbiDatabase, "hbiDatabase is required");
  }

  /** Get a connection to the HBI database. */
  private Connection getInsightsConnection() throws SQLException {
    return hbiDatabase.getConnection();
  }

  /**
   * Apply the RHEL per-socket increase mapping.
   *
   * <p>For physical RHEL hosts, certain socket counts are mapped to higher values for licensing
   * purposes.
   *
   * @param sockets the actual socket count
   * @return the mapped socket count according to RHEL_PER_SOCKET_INCREASE, or the original value if
   *     no mapping exists
   */
  public static int applyRhelSocketIncrease(int sockets) {
    return RHEL_PER_SOCKET_INCREASE.getOrDefault(sockets, sockets);
  }

  /**
   * Get the RHEL per-socket increase mapping.
   *
   * @return immutable map of socket count -> increased socket count
   */
  public static java.util.Map<Integer, Integer> getRhelPerSocketIncreaseMap() {
    return RHEL_PER_SOCKET_INCREASE;
  }

  /** Build facts JSON for RHEL host with product and capacity information. */
  private String buildRhelFacts(int cores, int sockets) {
    return String.format(
        """
        {
          "rhsm": {
            "RH_PROD": ["%s"],
            "IS_VIRTUAL": "false",
            "ARCHITECTURE": "x86_64",
            "CORES": "%d",
            "SOCKETS": "%d"
          }
        }
        """,
        RHEL_PRODUCT_ID, cores, sockets);
  }

  /** Record of a seeded host for test assertions. */
  public record SeededHost(
      UUID hostId, String inventoryId, String subscriptionManagerId, String orgId) {}

  // --- Convenience methods for easy test data creation ---

  /**
   * Insert a RHEL host with all default values.
   *
   * <p>Uses default inventoryId, subscriptionManagerId, displayName, cores (4), and sockets (2).
   *
   * @param orgId the organization ID
   * @return seeded host record
   */
  public SeededHost insertRhelHost(String orgId) {
    return insertRhelHost(orgId, null, null, null, DEFAULT_CORES, DEFAULT_SOCKETS);
  }

  /**
   * Insert a RHEL host with custom cores/sockets, other values use defaults.
   *
   * @param orgId the organization ID
   * @param cores number of CPU cores
   * @param sockets number of CPU sockets
   * @return seeded host record
   */
  public SeededHost insertRhelHost(String orgId, int cores, int sockets) {
    return insertRhelHost(orgId, null, null, null, cores, sockets);
  }

  /**
   * Insert a cloud/non-RHEL host with default values.
   *
   * <p>Uses default inventoryId, subscriptionManagerId, displayName, and providerId.
   *
   * @param orgId the organization ID
   * @return seeded host record
   */
  public SeededHost insertCloudHost(String orgId) {
    return insertNonRhelHost(orgId, null, null, null, null);
  }

  /**
   * Insert a RHEL host into the HBI database (insights schema).
   *
   * <p><b>IMPORTANT:</b> This method requires the production HBI database schema. Ensure all HBI
   * database migrations have been run before executing tests. The production schema uses
   * canonical_facts (JSONB) to store insights_id and subscription_manager_id, not direct columns.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID (must be unique)
   * @param subscriptionManagerId the subscription manager ID
   * @param displayName the display name for the host
   * @param cores number of CPU cores (for system_profile)
   * @param sockets number of CPU sockets (for system_profile)
   * @return seeded host record
   */
  public SeededHost insertRhelHost(
      String orgId,
      String inventoryId,
      String subscriptionManagerId,
      String displayName,
      int cores,
      int sockets) {
    Objects.requireNonNull(orgId, "orgId is required");

    // Use defaults if null values passed
    String actualInventoryId = (inventoryId != null) ? inventoryId : DEFAULT_INVENTORY_ID;
    String actualSubManId =
        (subscriptionManagerId != null) ? subscriptionManagerId : DEFAULT_SUBMAN_ID;
    String actualDisplayName = (displayName != null) ? displayName : DEFAULT_DISPLAY_NAME;

    UUID hostId = UUID.randomUUID();
    UUID insightsId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleTimestamp = now.plusDays(7); // Default: 7 days from now

    try (Connection conn = getInsightsConnection()) {
      // Production HBI schema: canonical_facts stores insights_id and subscription_manager_id
      String canonicalFacts =
          String.format(
              """
              {
                "insights_id": "%s",
                "subscription_manager_id": "%s"
              }
              """,
              insightsId, actualSubManId);

      String hostSql =
          """
          INSERT INTO hbi.hosts
            (id, org_id, display_name, canonical_facts, created_on, modified_on,
             stale_timestamp, facts, groups, reporter)
          VALUES
            (?, ?, ?, ?::jsonb, ?, ?,
             ?, ?::jsonb, ?::jsonb, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(hostSql)) {
        ps.setObject(1, hostId);
        ps.setString(2, orgId);
        ps.setString(3, actualDisplayName);
        ps.setString(4, canonicalFacts); // canonical_facts as JSONB
        ps.setObject(5, now); // created_on
        ps.setObject(6, now); // modified_on
        ps.setObject(7, staleTimestamp); // stale_timestamp
        ps.setString(8, buildRhelFacts(cores, sockets)); // facts
        ps.setString(9, "[]"); // groups - empty array (required NOT NULL)
        ps.setString(10, "component-test"); // reporter
        ps.executeUpdate();
      }

      // Insert into system_profiles_static table with capacity data
      String profileSql =
          """
          INSERT INTO hbi.system_profiles_static
            (org_id, host_id, cores_per_socket, number_of_sockets,
             infrastructure_type, arch)
          VALUES
            (?, ?, ?, ?,
             ?, ?)
          """;

      if (sockets == 0) {
        throw new RuntimeException("Sockets are 0!");
      }

      try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
        ps.setString(1, orgId);
        ps.setObject(2, hostId);
        ps.setInt(3, cores / sockets); // cores per socket
        ps.setInt(4, sockets);
        ps.setString(5, "physical");
        ps.setString(6, "x86_64");
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert RHEL host into HBI database", e);
    }

    insertedHostIds.add(hostId);
    return new SeededHost(hostId, actualInventoryId, actualSubManId, orgId);
  }

  /**
   * Insert a non-RHEL host into the HBI database.
   *
   * <p><b>IMPORTANT:</b> This method requires the production HBI database schema. Ensure all HBI
   * database migrations have been run before executing tests.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID
   * @param subscriptionManagerId the subscription manager ID
   * @param displayName the display name
   * @param providerId the cloud provider ID (for cloud hosts, e.g., AWS instance ID)
   * @return seeded host record
   */
  public SeededHost insertNonRhelHost(
      String orgId,
      String inventoryId,
      String subscriptionManagerId,
      String displayName,
      String providerId) {
    Objects.requireNonNull(orgId, "orgId is required");

    // Use defaults if null values passed
    String actualInventoryId = (inventoryId != null) ? inventoryId : DEFAULT_INVENTORY_ID;
    String actualSubManId =
        (subscriptionManagerId != null) ? subscriptionManagerId : DEFAULT_SUBMAN_ID;
    String actualDisplayName = (displayName != null) ? displayName : DEFAULT_CLOUD_DISPLAY_NAME;
    String actualProviderId = (providerId != null) ? providerId : DEFAULT_PROVIDER_ID;

    UUID hostId = UUID.randomUUID();
    UUID insightsId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleTimestamp = now.plusDays(7); // Default: 7 days from now

    try (Connection conn = getInsightsConnection()) {
      // Production HBI schema: canonical_facts stores provider_id, insights_id, subscription_manager_id
      String canonicalFacts =
          String.format(
              """
              {
                "insights_id": "%s",
                "subscription_manager_id": "%s",
                "provider_id": "%s"
              }
              """,
              insightsId, actualSubManId, actualProviderId);

      String hostSql =
          """
          INSERT INTO hbi.hosts
            (id, org_id, display_name, canonical_facts, created_on, modified_on,
             stale_timestamp, facts, groups, reporter)
          VALUES
            (?, ?, ?, ?::jsonb, ?, ?,
             ?, ?::jsonb, ?::jsonb, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(hostSql)) {
        ps.setObject(1, hostId);
        ps.setString(2, orgId);
        ps.setString(3, actualDisplayName);
        ps.setString(4, canonicalFacts); // canonical_facts as JSONB
        ps.setObject(5, now); // created_on
        ps.setObject(6, now); // modified_on
        ps.setObject(7, staleTimestamp); // stale_timestamp
        ps.setString(8, "{}"); // facts (empty for cloud hosts)
        ps.setString(9, "[]"); // groups - empty array (required NOT NULL)
        ps.setString(10, "component-test"); // reporter
        ps.executeUpdate();
      }

      // Insert into system_profiles_static table for cloud host
      // Cloud hosts may not have cores/sockets, but the record must exist for INNER JOIN
      String profileSql =
          """
          INSERT INTO hbi.system_profiles_static
            (org_id, host_id, infrastructure_type, cloud_provider, arch)
          VALUES
            (?, ?, ?, ?, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
        ps.setString(1, orgId);
        ps.setObject(2, hostId);
        ps.setString(3, "virtual"); // Cloud hosts are virtual
        ps.setString(4, "aws"); // Default to AWS for cloud hosts
        ps.setString(5, "x86_64");
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert non-RHEL host into HBI database", e);
    }

    insertedHostIds.add(hostId);
    return new SeededHost(hostId, actualInventoryId, actualSubManId, orgId);
  }

  /**
   * Delete a specific host by ID from the HBI database.
   *
   * @param hostId the host UUID to delete
   */
  public void deleteHost(UUID hostId) {
    Objects.requireNonNull(hostId, "hostId is required");

    try (Connection conn = getInsightsConnection()) {
      // Delete from system_profiles_static first (foreign key)
      try (PreparedStatement ps =
          conn.prepareStatement("DELETE FROM hbi.system_profiles_static WHERE host_id = ?")) {
        ps.setObject(1, hostId);
        ps.executeUpdate();
      }

      // Then delete from hosts
      try (PreparedStatement ps = conn.prepareStatement("DELETE FROM hbi.hosts WHERE id = ?")) {
        ps.setObject(1, hostId);
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete host from HBI database: " + hostId, e);
    }

    insertedHostIds.remove(hostId);
  }

  /**
   * Delete all hosts inserted by this seeder (rollback support).
   *
   * <p>This method should be called in test cleanup (e.g., @AfterEach) to ensure proper rollback on
   * test failure.
   */
  public void deleteAllInsertedHosts() {
    // Create a copy to avoid ConcurrentModificationException
    List<UUID> hostsToDelete = new ArrayList<>(insertedHostIds);
    for (UUID hostId : hostsToDelete) {
      try {
        deleteHost(hostId);
      } catch (Exception e) {
        // Log but continue cleanup
        System.err.println("Failed to delete HBI host " + hostId + ": " + e.getMessage());
      }
    }
    insertedHostIds.clear();
  }

  /**
   * Get the count of hosts currently tracked by this seeder.
   *
   * @return number of inserted hosts
   */
  public int getInsertedHostCount() {
    return insertedHostIds.size();
  }

  /**
   * Check if a host exists in the HBI database.
   *
   * @param hostId the host UUID to check
   * @return true if the host exists, false otherwise
   */
  public boolean hostExists(UUID hostId) {
    Objects.requireNonNull(hostId, "hostId is required");

    try (Connection conn = getInsightsConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM hbi.hosts WHERE id = ?")) {
      ps.setObject(1, hostId);
      try (var rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check if host exists in HBI database: " + hostId, e);
    }
  }
}
