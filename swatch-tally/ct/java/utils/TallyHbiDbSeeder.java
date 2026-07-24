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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.api.db.DatabaseService;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private static final String DEFAULT_PROVIDER_ID = "i-test-instance-";
  private static final int DEFAULT_CORES = 4;
  private static final int DEFAULT_SOCKETS = 2;
  private static final String RHEL_PRODUCT_ID = "69"; // RHEL product ID

  private final List<UUID> insertedHostIds = new ArrayList<>();
  private boolean schemaVerified = false;
  // Socket increase mapping for RHEL physical hosts
  // Maps actual socket count -> reported socket count for tally
  private static final Map<Integer, Integer> RHEL_PER_SOCKET_INCREASE =
      Map.of(1, 2, 2, 2, 4, 4, 7, 8);

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
  public static Map<Integer, Integer> getRhelPerSocketIncreaseMap() {
    return RHEL_PER_SOCKET_INCREASE;
  }

  /** Record of RHEL facts. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record RhsmFacts(
      @JsonProperty("RH_PROD") List<String> rhProd,
      @JsonProperty("IS_VIRTUAL") String isVirtual,
      @JsonProperty("ARCHITECTURE") String architecture,
      @JsonProperty("CORES") String cores,
      @JsonProperty("SOCKETS") String sockets,
      @JsonProperty("SYSPURPOSE_SLA") String syspurposeSla,
      @JsonProperty("SYSPURPOSE_USAGE") String syspurposeUsage) {}

  /** Record of the top level facts */
  private record Facts(RhsmFacts rhsm) {}

  /** Build facts JSON for RHEL host with product and capacity information. */
  private String buildRhelFacts(
      int cores, int sockets, boolean isVirtual, String sla, String usage) {
    RhsmFacts rhsm =
        new RhsmFacts(
            List.of(RHEL_PRODUCT_ID),
            isVirtual ? "true" : "false",
            "x86_64",
            String.valueOf(cores),
            String.valueOf(sockets),
            sla,
            usage);

    Facts facts = new Facts(rhsm);
    try {
      return new ObjectMapper().writeValueAsString(facts);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize RHEL facts", e);
    }
  }

  private void waitForSchemaReady() {
    if (schemaVerified) {
      return;
    }
    Log.info("Waiting for HBI schema to be ready...");
    AwaitilityUtils.untilIsTrue(
        () -> {
          try (Connection conn = getInsightsConnection();
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

  /** Record of a seeded host for test assertions. */
  public record SeededHost(
      UUID hostId, String inventoryId, String subscriptionManagerId, String orgId) {}

  public RhelHostBuilder rhelHost(String orgId) {
    return new RhelHostBuilder(orgId);
  }

  public CloudHostBuilder cloudHost(String orgId) {
    return new CloudHostBuilder(orgId);
  }

  /**
   * Builder for RHEL hosts. Defaults to physical infrastructure; call {@link #cloudProvider} to
   * create a "RHEL on cloud" host (virtual infrastructure with cloud provider metadata).
   */
  public class RhelHostBuilder {
    private final String orgId;
    private String inventoryId;
    private String subscriptionManagerId;
    private String displayName;
    private int cores = DEFAULT_CORES;
    private int sockets = DEFAULT_SOCKETS;
    private String reporter = "component-test";
    private String[] reporters = new String[] {"component-test"};
    private String cloudProvider;
    private String providerId;
    private String virtualHostUuid;
    private String sla;
    private String usage;

    private RhelHostBuilder(String orgId) {
      this.orgId = Objects.requireNonNull(orgId, "orgId is required");
    }

    public RhelHostBuilder inventoryId(String inventoryId) {
      this.inventoryId = inventoryId;
      return this;
    }

    public RhelHostBuilder subscriptionManagerId(String subscriptionManagerId) {
      this.subscriptionManagerId = subscriptionManagerId;
      return this;
    }

    public RhelHostBuilder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public RhelHostBuilder cores(int cores) {
      this.cores = cores;
      return this;
    }

    public RhelHostBuilder sockets(int sockets) {
      this.sockets = sockets;
      return this;
    }

    public RhelHostBuilder reporter(String reporter) {
      this.reporter = reporter;
      return this;
    }

    public RhelHostBuilder reporters(String... reporters) {
      this.reporters = reporters;
      return this;
    }

    /**
     * Set the cloud provider (e.g. "aws") to create a RHEL-on-cloud host. When set, the host uses
     * virtual infrastructure and includes cloud provider metadata instead of physical.
     */
    public RhelHostBuilder cloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
      return this;
    }

    public RhelHostBuilder providerId(String providerId) {
      this.providerId = providerId;
      return this;
    }

    /**
     * Set the virtual host UUID to create a guest VM linked to a hypervisor. The value should match
     * the hypervisor host's subscriptionManagerId. When set, the host is treated as virtual
     * (IS_VIRTUAL=true, infrastructure_type=virtual).
     */
    public RhelHostBuilder virtualHostUuid(String virtualHostUuid) {
      this.virtualHostUuid = virtualHostUuid;
      return this;
    }

    public RhelHostBuilder sla(String sla) {
      this.sla = sla;
      return this;
    }

    public RhelHostBuilder usage(String usage) {
      this.usage = usage;
      return this;
    }

    public SeededHost insert() {
      return insertRhelHost(
          orgId,
          inventoryId,
          subscriptionManagerId,
          displayName,
          cores,
          sockets,
          reporter,
          reporters,
          cloudProvider,
          providerId,
          virtualHostUuid,
          sla,
          usage);
    }
  }

  /**
   * Builder for non-RHEL cloud hosts. For RHEL hosts on cloud infrastructure, use {@link
   * #rhelHost(String)} with {@link RhelHostBuilder#cloudProvider(String)} instead.
   */
  public class CloudHostBuilder {
    private final String orgId;
    private String inventoryId;
    private String subscriptionManagerId;
    private String displayName;
    private String providerId;
    private String reporter = "component-test";
    private String[] reporters = new String[] {"component-test"};

    private CloudHostBuilder(String orgId) {
      this.orgId = Objects.requireNonNull(orgId, "orgId is required");
    }

    public CloudHostBuilder inventoryId(String inventoryId) {
      this.inventoryId = inventoryId;
      return this;
    }

    public CloudHostBuilder subscriptionManagerId(String subscriptionManagerId) {
      this.subscriptionManagerId = subscriptionManagerId;
      return this;
    }

    public CloudHostBuilder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public CloudHostBuilder providerId(String providerId) {
      this.providerId = providerId;
      return this;
    }

    public CloudHostBuilder reporter(String reporter) {
      this.reporter = reporter;
      return this;
    }

    public CloudHostBuilder reporters(String... reporters) {
      this.reporters = reporters;
      return this;
    }

    public SeededHost insert() {
      return insertNonRhelHost(
          orgId, inventoryId, subscriptionManagerId, displayName, providerId, reporter, reporters);
    }
  }

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
    return insertRhelHost(
        orgId,
        null,
        DEFAULT_SUBMAN_ID + "-" + (UUID.randomUUID()).toString().substring(0, 5),
        null,
        DEFAULT_CORES,
        DEFAULT_SOCKETS,
        "component-test",
        new String[] {"component-test"});
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
    return insertRhelHost(
        orgId,
        null,
        DEFAULT_SUBMAN_ID + "-" + (UUID.randomUUID()).toString().substring(0, 5),
        null,
        cores,
        sockets,
        "component-test",
        new String[] {"component-test"});
  }

  /**
   * Insert a RHEL host with custom cores/sockets, other values use defaults.
   *
   * @param orgId the organization ID
   * @param subscriptionManagerId the subscription manager ID
   * @param cores number of CPU cores
   * @param sockets number of CPU sockets
   * @return seeded host record
   */
  public SeededHost insertRhelHost(
      String orgId, String subscriptionManagerId, int cores, int sockets) {
    return insertRhelHost(
        orgId,
        null,
        subscriptionManagerId,
        null,
        cores,
        sockets,
        "component-test",
        new String[] {"component-test"});
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
    return insertNonRhelHost(
        orgId,
        null,
        DEFAULT_SUBMAN_ID + "-" + (UUID.randomUUID()).toString().substring(0, 5),
        null,
        null,
        "component-test",
        new String[] {"component-test"});
  }

  /**
   * Insert a physical RHEL host (no cloud provider). Delegates to the full method.
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
      int sockets,
      String reporter,
      String[] reporters) {
    return insertRhelHost(
        orgId,
        inventoryId,
        subscriptionManagerId,
        displayName,
        cores,
        sockets,
        reporter,
        reporters,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Insert a RHEL host into the HBI database. When cloudProvider is null, creates a physical host.
   * When cloudProvider is set (e.g. "aws"), creates a virtual cloud host with RHEL product facts.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID (must be unique)
   * @param subscriptionManagerId the subscription manager ID
   * @param displayName the display name for the host
   * @param cores number of CPU cores (for system_profile)
   * @param sockets number of CPU sockets (for system_profile)
   * @param reporter the reporter string
   * @param reporters the reporters array
   * @param cloudProvider cloud provider name (null for physical, e.g. "aws" for cloud)
   * @param providerId cloud provider instance ID (null for physical)
   * @param virtualHostUuid hypervisor's subscriptionManagerId for guest VMs (null for
   *     physical/cloud)
   * @return seeded host record
   */
  public SeededHost insertRhelHost(
      String orgId,
      String inventoryId,
      String subscriptionManagerId,
      String displayName,
      int cores,
      int sockets,
      String reporter,
      String[] reporters,
      String cloudProvider,
      String providerId,
      String virtualHostUuid,
      String sla,
      String usage) {

    waitForSchemaReady();
    Objects.requireNonNull(orgId, "orgId is required");

    boolean isCloud = cloudProvider != null;
    boolean isGuest = virtualHostUuid != null;

    // Use defaults if null values passed
    String actualInventoryId =
        (inventoryId != null)
            ? inventoryId
            : DEFAULT_INVENTORY_ID + "-" + (UUID.randomUUID()).toString().substring(0, 8);
    String actualSubManId = subscriptionManagerId; // TO ALLOW NULL SUB MAN ID
    String actualDisplayName =
        (displayName != null)
            ? displayName
            : DEFAULT_DISPLAY_NAME + "-" + (UUID.randomUUID()).toString().substring(0, 8);
    String actualReporter = (reporter != null) ? reporter : "component-test";
    String[] actualReporters = (reporters != null) ? reporters : new String[] {"component-test"};
    String actualProviderId =
        isCloud
            ? (providerId != null
                ? providerId
                : DEFAULT_PROVIDER_ID + UUID.randomUUID().toString().substring(0, 8))
            : null;

    UUID hostId = UUID.randomUUID();
    UUID insightsId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // Validate sockets before INSERT to prevent orphaned records
    if (sockets == 0) {
      throw new IllegalArgumentException(
          "Sockets cannot be 0 (would cause division by zero when calculating cores_per_socket)");
    }

    // Track host after validation but before INSERT, so cleanup works even if INSERT fails
    insertedHostIds.add(hostId);

    try (Connection conn = getInsightsConnection()) {
      String hostSql =
          """
          INSERT INTO hbi.hosts
            (id, org_id, display_name, insights_id, subscription_manager_id, provider_id,
             created_on, modified_on, last_check_in,
             facts, groups, reporter, reporters)
          VALUES
            (?, ?, ?, ?, ?, ?,
             ?, ?, ?,
             ?::jsonb, ?::jsonb, ?, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(hostSql)) {
        ps.setObject(1, hostId);
        ps.setString(2, orgId);
        ps.setString(3, actualDisplayName);
        ps.setObject(4, insightsId);
        ps.setString(5, actualSubManId);
        ps.setString(6, actualProviderId); // null for physical hosts
        ps.setObject(7, now);
        ps.setObject(8, now);
        ps.setObject(9, now);
        ps.setString(10, buildRhelFacts(cores, sockets, isGuest, sla, usage));
        ps.setString(11, "[]");
        ps.setString(12, actualReporter);
        ps.setArray(13, conn.createArrayOf("varchar", actualReporters));
        ps.executeUpdate();
      }

      String profileSql =
          """
          INSERT INTO hbi.system_profiles_static
            (org_id, host_id, cores_per_socket, number_of_sockets,
             infrastructure_type, cloud_provider, arch, virtual_host_uuid)
          VALUES
            (?, ?, ?, ?,
             ?, ?, ?, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
        ps.setString(1, orgId);
        ps.setObject(2, hostId);
        ps.setInt(3, cores / sockets);
        ps.setInt(4, sockets);
        ps.setString(5, (isCloud || isGuest) ? "virtual" : "physical");
        ps.setString(6, cloudProvider); // null for physical hosts
        ps.setString(7, "x86_64");
        if (virtualHostUuid != null) {
          ps.setObject(8, UUID.fromString(virtualHostUuid));
        } else {
          ps.setNull(8, Types.OTHER);
        }
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      String errorMessage = "Failed to insert RHEL host into HBI database";
      String message = e.getMessage();
      if (message != null && message.contains("does not exist")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Required HBI table or column is missing."
                + "\nThis usually means:"
                + "\n  1. HBI database migrations haven't been run (local: see README for setup)"
                + "\n  2. Schema is outdated (EE: check if host-inventory-run-db-migrations job completed)"
                + "\n  3. Wrong database selected (verify you're connecting to 'insights' database)"
                + "\n\nOriginal error: "
                + message;
      } else if (message != null
          && message.contains("is of type")
          && message.contains("but expression is of type")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Column exists but has wrong data type."
                + "\nThis usually means the HBI schema is outdated or incompatible."
                + "\nOriginal error: "
                + message;
      }
      throw new RuntimeException(errorMessage, e);
    }

    return new SeededHost(hostId, actualInventoryId, actualSubManId, orgId);
  }

  /**
   * Insert a non-RHEL cloud host into the HBI database with empty facts and no capacity data.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID
   * @param subscriptionManagerId the subscription manager ID
   * @param displayName the display name
   * @param providerId the cloud provider instance ID (e.g., AWS instance ID)
   * @param reporter the reporter string
   * @param reporters the reporters array
   * @return seeded host record
   */
  public SeededHost insertNonRhelHost(
      String orgId,
      String inventoryId,
      String subscriptionManagerId,
      String displayName,
      String providerId,
      String reporter,
      String[] reporters) {
    waitForSchemaReady();
    Objects.requireNonNull(orgId, "orgId is required");

    // Use defaults if null values passed
    String actualInventoryId =
        (inventoryId != null)
            ? inventoryId
            : DEFAULT_INVENTORY_ID + "-" + UUID.randomUUID().toString().substring(0, 8);
    String actualSubManId = subscriptionManagerId; // TO ALLOW NULL SUB MAN ID
    String actualDisplayName =
        (displayName != null)
            ? displayName
            : DEFAULT_CLOUD_DISPLAY_NAME + "-" + UUID.randomUUID().toString().substring(0, 8);
    String actualProviderId =
        (providerId != null)
            ? providerId
            : DEFAULT_PROVIDER_ID + UUID.randomUUID().toString().substring(0, 8);
    String actualReporter = (reporter != null) ? reporter : "component-test";
    String[] actualReporters = (reporters != null) ? reporters : new String[] {"component-test"};

    UUID hostId = UUID.randomUUID();
    UUID insightsId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // Track host before INSERT, so cleanup works even if INSERT fails
    insertedHostIds.add(hostId);

    try (Connection conn = getInsightsConnection()) {
      String hostSql =
          """
          INSERT INTO hbi.hosts
            (id, org_id, display_name, insights_id, subscription_manager_id, provider_id,
             created_on, modified_on, last_check_in,
             facts, groups, reporter, reporters)
          VALUES
            (?, ?, ?, ?, ?, ?,
             ?, ?, ?,
             ?::jsonb, ?::jsonb, ?, ?)
          """;

      try (PreparedStatement ps = conn.prepareStatement(hostSql)) {
        ps.setObject(1, hostId);
        ps.setString(2, orgId);
        ps.setString(3, actualDisplayName);
        ps.setObject(4, insightsId);
        ps.setString(5, actualSubManId);
        ps.setString(6, actualProviderId);
        ps.setObject(7, now);
        ps.setObject(8, now);
        ps.setObject(9, now);
        ps.setString(10, "{}");
        ps.setString(11, "[]");
        ps.setString(12, actualReporter);
        ps.setArray(13, conn.createArrayOf("varchar", actualReporters));
        ps.executeUpdate();
      }

      // Record must exist for INNER JOIN in tally sync query
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
        ps.setString(3, "virtual");
        ps.setString(4, "aws");
        ps.setString(5, "x86_64");
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      String errorMessage = "Failed to insert cloud/non-RHEL host into HBI database";
      String message = e.getMessage();
      if (message != null && message.contains("does not exist")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Required HBI table or column is missing."
                + "\nThis usually means:"
                + "\n  1. HBI database migrations haven't been run (local: see README for setup)"
                + "\n  2. Schema is outdated (EE: check if host-inventory-run-db-migrations job completed)"
                + "\n  3. Wrong database selected (verify you're connecting to 'insights' database)"
                + "\n\nOriginal error: "
                + message;
      } else if (message != null
          && message.contains("is of type")
          && message.contains("but expression is of type")) {
        errorMessage +=
            "\n\nSCHEMA ERROR: Column exists but has wrong data type."
                + "\nThis usually means the HBI schema is outdated or incompatible."
                + "\nOriginal error: "
                + message;
      }
      throw new RuntimeException(errorMessage, e);
    }

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
