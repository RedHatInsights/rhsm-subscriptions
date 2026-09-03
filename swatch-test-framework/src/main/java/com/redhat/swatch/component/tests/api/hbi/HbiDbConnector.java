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

    validateSystemProfile(host);

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

    return new SeededHost(hostId, subscriptionManagerId, host.getOrgId());
  }

  @Override
  public SeededHost update(Host host) {
    UUID hostId =
        Objects.requireNonNull(
            host.getId(), "host.getId() is required to update - was this host ever seed()-ed?");

    verifySchema();
    validateSystemProfile(host);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime modifiedOn = host.getModifiedOn() != null ? host.getModifiedOn() : now;
    OffsetDateTime lastCheckIn = host.getLastCheckIn() != null ? host.getLastCheckIn() : now;

    try (Connection conn = hbiDatabase.getConnection()) {
      updateHost(conn, hostId, host, modifiedOn, lastCheckIn);
      deleteSystemProfile(conn, hostId);
      insertSystemProfile(conn, hostId, host);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update host in HBI database: " + hostId, e);
    }

    return new SeededHost(hostId, host.getSubscriptionManagerId(), host.getOrgId());
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

  private void validateSystemProfile(Host host) {
    SystemProfileFacts profile = host.getSystemProfileFacts();
    if (profile != null
        && profile.getNumberOfSockets() != null
        && profile.getNumberOfSockets() == 0) {
      throw new IllegalArgumentException(
          "Sockets cannot be 0 (would cause division by zero when calculating cores_per_socket)");
    }
  }

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

  private void updateHost(
      Connection conn,
      UUID hostId,
      Host host,
      OffsetDateTime modifiedOn,
      OffsetDateTime lastCheckIn)
      throws SQLException {

    String sql =
        """
        UPDATE hbi.hosts
        SET display_name = ?, subscription_manager_id = ?, provider_id = ?,
            modified_on = ?, last_check_in = ?, facts = ?::jsonb, reporter = ?, reporters = ?
        WHERE id = ?
        """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(
          1, host.getDisplayName() != null ? host.getDisplayName() : "Test Host (auto-generated)");
      ps.setString(2, host.getSubscriptionManagerId());
      ps.setString(3, host.getProviderId());
      ps.setObject(4, modifiedOn);
      ps.setObject(5, lastCheckIn);
      ps.setString(6, buildFactsJson(host));
      ps.setString(7, host.getReporter());
      ps.setArray(8, conn.createArrayOf("varchar", host.getReporters()));
      ps.setObject(9, hostId);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        throw new SQLException("No host found in hbi.hosts with id " + hostId + " to update");
      }
    } catch (SQLException e) {
      String errorMessage = "Failed to update host in hbi.hosts: " + hostId;
      if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Required HBI table or column is missing."
                + "\nOriginal error: "
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

    SystemProfileFacts profile = host.getSystemProfileFacts();

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, host.getOrgId());
      ps.setObject(2, hostId);

      setNullableInt(ps, 3, profile == null ? null : profile.getCoresPerSocket());
      setNullableInt(ps, 4, profile == null ? null : profile.getNumberOfSockets());
      setNullableInt(ps, 5, profile == null ? null : profile.getNumberOfCpus());
      setNullableInt(ps, 6, profile == null ? null : profile.getThreadsPerCore());

      ps.setString(7, profile == null ? null : profile.getInfrastructureType());
      ps.setString(8, profile == null ? null : profile.getCloudProvider());
      ps.setString(9, profile == null ? null : profile.getArch());

      Boolean isMarketplace = profile == null ? null : profile.getIsMarketplace();
      if (isMarketplace != null) {
        ps.setBoolean(10, isMarketplace);
      } else {
        ps.setNull(10, java.sql.Types.BOOLEAN);
      }

      ps.setString(11, profile == null ? null : profile.getHypervisorUuid());
      ps.setString(12, profile == null ? null : profile.getHostType());

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

    // Add fact groups if they are present
    if (host.getRhsmFacts() != null) {
      facts.put("rhsm", host.getRhsmFacts().toMap());
    }
    if (host.getQpcFacts() != null) {
      facts.put("qpc", host.getQpcFacts().toMap());
    }
    if (host.getSatelliteFacts() != null) {
      facts.put("satellite", host.getSatelliteFacts().toMap());
    }
    if (host.getYupanaFacts() != null) {
      facts.put("yupana", host.getYupanaFacts().toMap());
    }

    try {
      return objectMapper.writeValueAsString(facts);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize facts to JSON", e);
    }
  }

  private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
    if (value != null) {
      ps.setInt(index, value);
    } else {
      ps.setNull(index, java.sql.Types.INTEGER);
    }
  }
}
