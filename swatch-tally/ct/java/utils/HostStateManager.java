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

import com.redhat.swatch.component.tests.logging.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import utils.HostConnector.SeededHost;

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

  // RHEL product ID for templates
  private static final String RHEL_PRODUCT_ID = "69";

  // Socket increase mapping for physical RHEL hosts
  private static final Map<Integer, Integer> RHEL_SOCKET_INCREASE = Map.of(1, 2, 2, 2, 4, 4, 7, 8);

  public HostStateManager(HostConnector connector) {
    this.connector = Objects.requireNonNull(connector, "connector is required");
  }

  // ===== Entry Points for Different Reporter Types =====

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
  public HostBuilder qpc(Host host) {
    Host copy = host.copy();
    applyQpcDefaults(copy);
    return new HostBuilder(this, copy);
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
  public HostBuilder qpc(String orgId) {
    Host host = new Host(orgId);
    applyQpcDefaults(host);
    return new HostBuilder(this, host);
  }

  /**
   * Apply QPC-specific defaults (only if not already set).
   *
   * <p>Defaults applied:
   *
   * <ul>
   *   <li>reporter: "qpc" (if not set)
   *   <li>reporters: ["qpc"] (if not set)
   *   <li>arch: "x86_64" (if not set)
   * </ul>
   *
   * @param host the host to apply defaults to
   */
  private void applyQpcDefaults(Host host) {
    // Apply reporter defaults (only if not set)
    if (host.getReporter() == null || "component-test".equals(host.getReporter())) {
      host.reporter("qpc");
    }

    if (host.getReporters() == null
        || (host.getReporters().length == 1 && "component-test".equals(host.getReporters()[0]))) {
      host.reporters("qpc");
    }

    if (host.getArch() == null) {
      host.arch("x86_64");
    }
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
  public HostBuilder satellite(Host host) {
    Host copy = host.copy();
    applySatelliteDefaults(copy);
    return new HostBuilder(this, copy);
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
  public HostBuilder satellite(String orgId) {
    Host host = new Host(orgId);
    applySatelliteDefaults(host);
    return new HostBuilder(this, host);
  }

  /**
   * Apply Satellite-specific defaults (only if not already set).
   *
   * <p>Based on common values from FactNormalizerTest and production usage.
   *
   * <p>Defaults applied:
   *
   * <ul>
   *   <li>reporter: "satellite" (if not set)
   *   <li>reporters: ["satellite"] (if not set)
   *   <li>arch: "x86_64" (if not set)
   *   <li>system_purpose_role: "Red Hat Enterprise Linux Server" (if not set in satellite facts)
   *   <li>system_purpose_sla: "Premium" (if not set in satellite facts)
   *   <li>system_purpose_usage: "Production" (if not set in satellite facts)
   * </ul>
   *
   * @param host the host to apply defaults to
   */
  private void applySatelliteDefaults(Host host) {
    // Apply reporter defaults (only if not set)
    if (host.getReporter() == null || "component-test".equals(host.getReporter())) {
      host.reporter("satellite");
    }

    if (host.getReporters() == null
        || (host.getReporters().length == 1 && "component-test".equals(host.getReporters()[0]))) {
      host.reporters("satellite");
    }

    if (host.getArch() == null) {
      host.arch("x86_64"); // ?
    }

    // Apply Satellite fact defaults (only if not already set)
    Map<String, Object> satelliteFacts = host.getSatelliteFacts();

    if (!satelliteFacts.containsKey("system_purpose_role")) {
      host.satelliteFact("system_purpose_role", "Red Hat Enterprise Linux Server");
    }

    if (!satelliteFacts.containsKey("system_purpose_sla")) {
      host.satelliteFact("system_purpose_sla", "Premium");
    }

    if (!satelliteFacts.containsKey("system_purpose_usage")) {
      host.satelliteFact("system_purpose_usage", "Production");
    }
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
      Log.warn("Failed to cleanup %d host(s)", failCount);
    }

    trackedHostIds.clear();
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
   * Get the count of tracked hosts.
   *
   * @return number of hosts currently tracked
   */
  public int getTrackedCount() {
    return trackedHostIds.size();
  }

  // ===== Inner HostBuilder Class =====

  /**
   * Builder for applying templates and customizations before inserting.
   *
   * <p>Provides template methods for common infrastructure configurations and override methods for
   * specific fields.
   */
  public class HostBuilder {
    private final HostStateManager manager;
    private final Host host;

    HostBuilder(HostStateManager manager, Host host) {
      this.manager = manager;
      this.host = host;
    }

    // ===== Physical RHEL Templates =====

    /**
     * Physical RHEL host with 1 socket, 1 core.
     *
     * <p>Applies RHEL socket increase (1 → 2 for tally).
     */
    public HostBuilder physicalRhel1Socket() {
      applyPhysicalRhelDefaults();
      host.sockets(1);
      host.cores(1);
      host.displayName(getOrDefault(host.getDisplayName(), "RHEL Physical 1-socket"));
      host.expectedTallySockets(RHEL_SOCKET_INCREASE.getOrDefault(1, 1));
      return this;
    }

    /**
     * Physical RHEL host with 2 sockets, 2 cores.
     *
     * <p>Applies RHEL socket increase (2 → 2 for tally).
     */
    public HostBuilder physicalRhel2Socket() {
      applyPhysicalRhelDefaults();
      host.sockets(2);
      host.cores(2);
      host.displayName(getOrDefault(host.getDisplayName(), "RHEL Physical 2-socket"));
      host.expectedTallySockets(RHEL_SOCKET_INCREASE.getOrDefault(2, 2));
      return this;
    }

    /**
     * Physical RHEL host with 4 sockets, 4 cores.
     *
     * <p>Applies RHEL socket increase (4 → 4 for tally).
     */
    public HostBuilder physicalRhel4Socketand4Cores() {
      applyPhysicalRhelDefaults();
      host.sockets(4);
      host.cores(4);
      host.displayName(getOrDefault(host.getDisplayName(), "RHEL Physical 4-socket"));
      //      host.expectedTallySockets(RHEL_SOCKET_INCREASE.getOrDefault(4, 4));
      return this;
    }

    /**
     * Physical RHEL host with 7 sockets, 7 cores.
     *
     * <p>Applies RHEL socket increase (7 → 8 for tally).
     */
    public HostBuilder physicalRhel7Socket() {
      applyPhysicalRhelDefaults();
      host.sockets(7);
      host.cores(7);
      host.displayName(getOrDefault(host.getDisplayName(), "RHEL Physical 7-socket"));
      host.expectedTallySockets(RHEL_SOCKET_INCREASE.getOrDefault(7, 7));
      return this;
    }

    /**
     * Physical RHEL host with 8 sockets, 8 cores.
     *
     * <p>No socket increase applied (8 → 8 for tally).
     */
    public HostBuilder physicalRhel8SocketsAnd8Cores() {
      applyPhysicalRhelDefaults();
      host.sockets(8);
      host.cores(8);
      host.displayName(getOrDefault(host.getDisplayName(), "RHEL Physical 8-socket"));
      // host.expectedTallySockets(RHEL_SOCKET_INCREASE.getOrDefault(8, 8));
      return this;
    }

    // ===== AWS RHEL Templates =====

    /**
     * AWS RHEL host - small instance (t3.medium equivalent).
     *
     * <p>Virtual, 2 sockets, 4 cores. No socket increase for cloud hosts.
     */
    public HostBuilder awsRhelSmall() {
      applyAwsRhelDefaults();
      host.sockets(2);
      host.cores(4);
      host.displayName(getOrDefault(host.getDisplayName(), "AWS RHEL Small (t3.medium)"));
      host.expectedTallySockets(2); // No increase for cloud
      return this;
    }

    /**
     * AWS RHEL host - medium instance (m5.4xlarge equivalent).
     *
     * <p>Virtual, 8 sockets, 16 cores. No socket increase for cloud hosts.
     */
    public HostBuilder awsRhelMedium() {
      applyAwsRhelDefaults();
      host.sockets(8);
      host.cores(16);
      host.displayName(getOrDefault(host.getDisplayName(), "AWS RHEL Medium (m5.4xlarge)"));
      host.expectedTallySockets(8);
      return this;
    }

    /**
     * AWS RHEL host - large instance (m5.16xlarge equivalent).
     *
     * <p>Virtual, 16 sockets, 64 cores. No socket increase for cloud hosts.
     */
    public HostBuilder awsRhel6Sockets64Cores() {
      applyAwsRhelDefaults();
      host.sockets(16);
      host.cores(64);
      host.displayName(getOrDefault(host.getDisplayName(), "AWS RHEL Large (m5.16xlarge)"));
      host.expectedTallySockets(16);
      return this;
    }

    // ===== Azure RHEL Templates =====

    /**
     * Azure RHEL host - small instance.
     *
     * <p>Virtual, 2 sockets, 4 cores. No socket increase for cloud hosts.
     */
    public HostBuilder azureRhelSmall() {
      applyAzureRhelDefaults();
      host.sockets(2);
      host.cores(4);
      host.displayName(getOrDefault(host.getDisplayName(), "Azure RHEL Small"));
      host.expectedTallySockets(2);
      return this;
    }

    /**
     * Azure RHEL host - large instance.
     *
     * <p>Virtual, 16 sockets, 64 cores. No socket increase for cloud hosts.
     */
    public HostBuilder azureRhelLarge() {
      applyAzureRhelDefaults();
      host.sockets(16);
      host.cores(64);
      host.displayName(getOrDefault(host.getDisplayName(), "Azure RHEL Large"));
      host.expectedTallySockets(16);
      return this;
    }

    // ===== Non-RHEL Cloud Templates =====

    /**
     * AWS non-RHEL cloud host.
     *
     * <p>Virtual, no RHEL product facts. Useful for testing filtering logic.
     */
    public HostBuilder awsNonRhel() {
      host.infrastructureType("virtual");
      host.cloudProvider("aws");
      host.arch("x86_64");
      host.rhsmFact("IS_VIRTUAL", "true");
      host.displayName(getOrDefault(host.getDisplayName(), "AWS Non-RHEL Instance"));
      return this;
    }

    // ===== Override Methods (Always Override) =====

    public HostBuilder orgId(String orgId) {
      host.orgId(orgId);
      return this;
    }

    public HostBuilder displayName(String displayName) {
      host.displayName(displayName);
      return this;
    }

    public HostBuilder inventoryId(String inventoryId) {
      host.inventoryId(inventoryId);
      return this;
    }

    public HostBuilder subscriptionManagerId(String subscriptionManagerId) {
      host.subscriptionManagerId(subscriptionManagerId);
      return this;
    }

    public HostBuilder cores(Integer cores) {
      host.cores(cores);
      return this;
    }

    public HostBuilder sockets(Integer sockets) {
      host.sockets(sockets);
      return this;
    }

    public HostBuilder arch(String arch) {
      host.arch(arch);
      return this;
    }

    public HostBuilder infrastructureType(String infrastructureType) {
      host.infrastructureType(infrastructureType);
      return this;
    }

    public HostBuilder cloudProvider(String cloudProvider) {
      host.cloudProvider(cloudProvider);
      return this;
    }

    public HostBuilder providerId(String providerId) {
      host.providerId(providerId);
      return this;
    }

    public HostBuilder rhsmFact(String key, Object value) {
      host.rhsmFact(key, value);
      return this;
    }

    public HostBuilder qpcFact(String key, Object value) {
      host.qpcFact(key, value);
      return this;
    }

    public HostBuilder satelliteFact(String key, Object value) {
      host.satelliteFact(key, value);
      return this;
    }

    // ===== QPC Fact Convenience Methods =====

    /**
     * Set the list of Red Hat products installed (detected by QPC scan).
     *
     * <p>Maps to h.facts->'qpc'->>'rh_products_installed' in the InventoryHost query.
     *
     * <p>Common product IDs: "69" (RHEL), "479" (RHEL for SAP), etc.
     */
    public HostBuilder qpcProductsInstalled(List<Object> productIds) {
      host.qpcFact("rh_products_installed", productIds);
      return this;
    }

    /**
     * Set whether QPC detected this host as RHEL.
     *
     * <p>Maps to h.facts->'qpc'->>'IS_RHEL' in the InventoryHost query.
     */
    public HostBuilder isRhel(boolean isRhel) {
      host.qpcFact("IS_RHEL", String.valueOf(isRhel));
      return this;
    }

    // ===== Satellite Fact Convenience Methods =====

    /**
     * Set the virtual host UUID (hypervisor UUID) in Satellite facts.
     *
     * <p>Maps to h.facts->'satellite'->>'virtual_host_uuid' in the InventoryHost query.
     */
    public HostBuilder hypervisorUuid(String uuid) {
      host.satelliteFact("virtual_host_uuid", uuid);
      return this;
    }

    /**
     * Set the system purpose role in Satellite facts.
     *
     * <p>Maps to h.facts->'satellite'->>'system_purpose_role' in the InventoryHost query.
     *
     * <p>Common values: "Red Hat Enterprise Linux Server", "Red Hat Enterprise Linux Workstation"
     */
    public HostBuilder systemPurposeRole(String role) {
      host.satelliteFact("system_purpose_role", role);
      return this;
    }

    /**
     * Set the system purpose SLA in Satellite facts.
     *
     * <p>Maps to h.facts->'satellite'->>'system_purpose_sla' in the InventoryHost query.
     *
     * <p>Common values: "Premium", "Standard", "Self-Support"
     */
    public HostBuilder systemPurposeSla(String sla) {
      host.satelliteFact("system_purpose_sla", sla);
      return this;
    }

    /**
     * Set the system purpose usage in Satellite facts.
     *
     * <p>Maps to h.facts->'satellite'->>'system_purpose_usage' in the InventoryHost query.
     *
     * <p>Common values: "Production", "Development/Test", "Disaster Recovery"
     */
    public HostBuilder systemPurposeUsage(String usage) {
      host.satelliteFact("system_purpose_usage", usage);
      return this;
    }

    // ===== Insert (Final Action) =====

    /**
     * Finalize and insert the host into HBI.
     *
     * <p>Calculates derived fields (cores_per_socket) and seeds via connector.
     *
     * @return information about the seeded host
     */
    public SeededHost insert() {
      // Calculate derived fields
      if (host.getCores() != null && host.getSockets() != null && host.getSockets() > 0) {
        if (host.getCoresPerSocket() == null) {
          host.coresPerSocket(host.getCores() / host.getSockets());
        }
      }

      // Seed via manager (which tracks the host)
      return manager.seed(host);
    }

    // ===== Private Template Helpers =====

    private void applyPhysicalRhelDefaults() {
      host.infrastructureType("physical");
      host.arch("x86_64");
      host.rhsmFact("IS_VIRTUAL", "false");
      host.rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID));
      host.rhsmFact("ARCHITECTURE", "x86_64");
    }

    private void applyAwsRhelDefaults() {
      host.infrastructureType("virtual");
      host.cloudProvider("aws");
      host.arch("x86_64");
      host.rhsmFact("IS_VIRTUAL", "true");
      host.rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID));
      host.rhsmFact("ARCHITECTURE", "x86_64");
    }

    private void applyAzureRhelDefaults() {
      host.infrastructureType("virtual");
      host.cloudProvider("azure");
      host.arch("x86_64");
      host.rhsmFact("IS_VIRTUAL", "true");
      host.rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID));
      host.rhsmFact("ARCHITECTURE", "x86_64");
    }

    private String getOrDefault(String value, String defaultValue) {
      return value != null ? value : defaultValue;
    }
  }
}
