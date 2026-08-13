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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.api.db.DatabaseService;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HBI database connector - inserts hosts directly into the HBI database.
 *
 * <p>This is the fastest approach for component tests but bypasses the Kafka ingestion pipeline.
 *
 * <p>Usage:
 *
 * <pre>
 * HbiDbConnector connector = new HbiDbConnector(hbiDatabase);
 * SeededHost seeded = connector.seed(hostDefinition);
 * </pre>
 */
public class HbiDbConnector implements HostConnector {

  private final DatabaseService hbiDatabase;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private boolean schemaVerified = false;

  public HbiDbConnector(DatabaseService hbiDatabase) {
    this.hbiDatabase = Objects.requireNonNull(hbiDatabase, "hbiDatabase is required");
  }

  @Override
  public SeededHost seed(Host host) {

    verifySchema();

    UUID insightsId =
        host.getInsightsId() != null ? UUID.fromString(host.getInsightsId()) : UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // Use provided timestamps or default to now
    OffsetDateTime createdOn = host.getCreatedOn() != null ? host.getCreatedOn() : now;
    OffsetDateTime modifiedOn = host.getModifiedOn() != null ? host.getModifiedOn() : now;
    OffsetDateTime lastCheckIn = host.getLastCheckIn() != null ? host.getLastCheckIn() : now;

    String subscriptionManagerId = host.getSubscriptionManagerId(); // Can be null

    // Validate sockets before INSERT
    if (host.getSockets() != null && host.getSockets() == 0) {
      throw new IllegalArgumentException(
          "Sockets cannot be 0 (would cause division by zero when calculating cores_per_socket)");
    }

    UUID hostId = null;
    try (Connection conn = hbiDatabase.getConnection()) {
      // Insert host and get the database-generated ID
      hostId = insertHost(conn, insightsId, host, createdOn, modifiedOn, lastCheckIn);
      insertSystemProfile(conn, hostId, host);
    } catch (SQLException e) {
      if (hostId != null && hostExists(hostId)) {
        // Host already exists, but we failed to insert the system profile
        // This is a rare case, but it can happen if the host is being re-inserted
        // after a previous failure.
        // In this case, we can safely ignore the error and continue.
        cleanup(hostId);
      }
      throw new RuntimeException("Failed to seed host into HBI database", e);
    }

    // Use the host ID as the inventory ID
    // In HBI, the hosts.id column serves as the inventory identifier.
    // There is no separate inventory_id column in the schema.
    String inventoryId = hostId.toString();

    return new SeededHost(hostId, inventoryId, subscriptionManagerId, host.getOrgId());
  }

  @Override
  public List<SeededHost> seedBatch(List<Host> hosts) {
    return hosts.stream().map(this::seed).collect(Collectors.toList());
  }

  @Override
  public void cleanup(UUID hostId) {
    try (Connection conn = hbiDatabase.getConnection()) {
      deleteSystemProfile(conn, hostId);
      deleteHost(conn, hostId);
      Log.debug("Cleaned up host: %s", hostId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to cleanup host: " + hostId, e);
    }
  }

  @Override
  public boolean hostExists(UUID hostId) {
    String sql = "SELECT 1 FROM hbi.hosts WHERE id = ? LIMIT 1";
    try (Connection conn = hbiDatabase.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setObject(1, hostId);
      var rs = ps.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check if host exists: " + hostId, e);
    }
  }

  // ===== Private Helper Methods =====

  private void verifySchema() {
    if (schemaVerified) {
      return;
    }
    Log.info("Waiting for HBI schema to be ready...");
    AwaitilityUtils.untilIsTrue(
        () -> {
          try (Connection conn = hbiDatabase.getConnection();
              PreparedStatement ps =
                  conn.prepareStatement(
                      "SELECT COUNT(*) FROM information_schema.tables"
                          + " WHERE table_schema = 'hbi'"
                          + " AND table_name IN ('hosts', 'system_profiles_static')")) {
            var rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) == 2;
          }
        },
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(120))
            .timeoutMessage(
                "HBI schema not ready after 120s - migration job may not have completed"));
    schemaVerified = true;
    Log.info("HBI schema is ready.");
  }

  private UUID insertHost(
      Connection conn,
      UUID insightsId,
      Host host,
      OffsetDateTime createdOn,
      OffsetDateTime modifiedOn,
      OffsetDateTime lastCheckIn)
      throws SQLException {

    String sql =
        """
        INSERT INTO hbi.hosts
          (id, org_id, display_name, insights_id, subscription_manager_id, provider_id,
           created_on, modified_on, last_check_in,
           facts, groups, reporter, reporters)
        VALUES
          (gen_random_uuid(), ?, ?, ?, ?, ?,
           ?, ?, ?,
           ?::jsonb, ?::jsonb, ?, ?)
        RETURNING id
        """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, host.getOrgId());
      ps.setString(
          2, host.getDisplayName() != null ? host.getDisplayName() : "Test Host (auto-generated)");
      ps.setObject(3, insightsId);
      ps.setString(4, host.getSubscriptionManagerId());
      ps.setString(5, host.getProviderId());
      ps.setObject(6, createdOn);
      ps.setObject(7, modifiedOn);
      ps.setObject(8, lastCheckIn);
      ps.setString(9, buildFactsJson(host));
      ps.setString(10, "[]"); // groups
      ps.setString(11, host.getReporter());
      ps.setArray(12, conn.createArrayOf("varchar", host.getReporters()));

      // Execute query and retrieve the database-generated ID
      var rs = ps.executeQuery();
      if (!rs.next()) {
        throw new SQLException("Failed to retrieve generated host ID from database");
      }
      UUID hostId = (UUID) rs.getObject("id");
      return hostId;
    } catch (SQLException e) {
      String errorMessage = "Failed to insert host into hbi.hosts";
      if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Required HBI table or column is missing."
                + "\nThis usually means:"
                + "\n  1. HBI database migrations haven't been run (local: see README for setup)"
                + "\n  2. Schema is outdated (EE: check if host-inventory-run-db-migrations job"
                + " completed)"
                + "\n  3. Wrong database selected (verify you're connecting to 'insights'"
                + " database)"
                + "\n\nOriginal error: "
                + e.getMessage();
      }
      throw new SQLException(errorMessage, e);
    }
  }

  private void insertSystemProfile(Connection conn, UUID hostId, Host host) throws SQLException {
    String sql =
        """
        INSERT INTO hbi.system_profiles_static
          (org_id, host_id, cores_per_socket, number_of_sockets, number_of_cpus,
           threads_per_core, infrastructure_type, cloud_provider, arch, is_marketplace,
           virtual_host_uuid, host_type)
        VALUES
          (?, ?, ?, ?, ?,
           ?, ?, ?, ?, ?,
           ?::uuid, ?)
        """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, host.getOrgId());
      ps.setObject(2, hostId);

      // Calculate cores_per_socket if not explicitly set
      Integer coresPerSocket = host.getCoresPerSocket();

      // Set nullable integers
      if (coresPerSocket != null) {
        ps.setInt(3, coresPerSocket);
      } else {
        ps.setNull(3, java.sql.Types.INTEGER);
      }

      if (host.getSockets() != null) {
        ps.setInt(4, host.getSockets());
      } else {
        ps.setNull(4, java.sql.Types.INTEGER);
      }

      if (host.getCores() != null) {
        ps.setInt(5, host.getCores());
      } else {
        ps.setNull(5, java.sql.Types.INTEGER);
      }

      if (host.getThreadsPerCore() != null) {
        ps.setInt(6, host.getThreadsPerCore());
      } else {
        ps.setNull(6, java.sql.Types.INTEGER);
      }

      ps.setString(7, host.getInfrastructureType());
      ps.setString(8, host.getCloudProvider());
      ps.setString(9, host.getArch());

      if (host.getIsMarketplace() != null) {
        ps.setBoolean(10, host.getIsMarketplace());
      } else {
        ps.setNull(10, java.sql.Types.BOOLEAN);
      }

      ps.setString(11, host.getVirtualHostUuid());
      ps.setString(12, host.getHostType());

      ps.executeUpdate();
    } catch (SQLException e) {
      String errorMessage = "Failed to insert into hbi.system_profiles_static";
      if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: system_profiles_static table or column missing."
                + "\nOriginal error: "
                + e.getMessage();
      }
      throw new SQLException(errorMessage, e);
    }
  }

  private void deleteSystemProfile(Connection conn, UUID hostId) throws SQLException {
    String sql = "DELETE FROM hbi.system_profiles_static WHERE host_id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setObject(1, hostId);
      ps.executeUpdate();
    }
  }

  private void deleteHost(Connection conn, UUID hostId) throws SQLException {
    String sql = "DELETE FROM hbi.hosts WHERE id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setObject(1, hostId);
      ps.executeUpdate();
    }
  }

  private String buildFactsJson(Host host) {
    Map<String, Object> facts = new HashMap<>();

    // Add fact groups if they have content
    if (!host.getRhsmFacts().isEmpty()) {
      facts.put("rhsm", host.getRhsmFacts());
    }
    if (!host.getQpcFacts().isEmpty()) {
      facts.put("qpc", host.getQpcFacts());
      facts.put("yupana", host.getYupanaFacts());
    }
    if (!host.getSatelliteFacts().isEmpty()) {
      facts.put("satellite", host.getSatelliteFacts());
      facts.put("yupana", host.getYupanaFacts());
    }

    try {
      return objectMapper.writeValueAsString(facts);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize facts to JSON", e);
    }
  }
}
