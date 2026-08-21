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
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * Builder for applying templates and customizations before inserting.
 *
 * <p>Provides template methods for common infrastructure configurations and override methods for
 * specific fields.
 */
public class HostBuilder {
  private final HostStateManager manager;
  private final Host host;
  private static final String RHEL_PRODUCT_ID = "69";

  HostBuilder(HostStateManager manager, Host host) {
    this.manager = manager;
    this.host = host;
  }

  // ===== Physical RHEL Templates =====

  /** Apply physical RHEL defaults (infrastructure=physical, IS_VIRTUAL=false, RHEL product). */
  public HostBuilder physicalRhelDefaults() {
    host.infrastructureType("physical");
    host.arch("x86_64");
    host.rhsmFact("IS_VIRTUAL", "false");
    host.rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID));
    host.rhsmFact("ARCHITECTURE", "x86_64");
    return this;
  }

  /** Physical RHEL host with 1 socket, 0 cores. */
  public HostBuilder physicalRhel1Socket0Cores() {
    physicalRhelDefaults();
    host.sockets(1);
    host.cores(0);
    host.displayName(getOrDefault(host.getDisplayName(), generateName("physical", "RHEL", 1, 0)));
    return this;
  }

  /** Physical RHEL host with 2 sockets, 2 cores. */
  public HostBuilder physicalRhel2Socket2Cores() {
    physicalRhelDefaults();
    host.sockets(2);
    host.cores(2);
    host.displayName(getOrDefault(host.getDisplayName(), generateName("physical", "RHEL", 2, 2)));
    return this;
  }

  /** Physical RHEL host with 8 sockets, 8 cores. */
  public HostBuilder physicalRhel8Sockets8Cores() {
    physicalRhelDefaults();
    host.sockets(8);
    host.cores(8);
    host.displayName(getOrDefault(host.getDisplayName(), generateName("physical", "RHEL", 8, 8)));
    return this;
  }

  // ===== AWS RHEL Templates =====

  /** Apply AWS RHEL defaults (infrastructure=virtual, cloudProvider=aws, RHEL product). */
  public HostBuilder awsRhelDefaults() {
    host.infrastructureType("virtual");
    host.cloudProvider("aws");
    host.arch("x86_64");
    host.rhsmFact("IS_VIRTUAL", "true");
    host.rhsmFact("RH_PROD", List.of(RHEL_PRODUCT_ID));
    host.rhsmFact("ARCHITECTURE", "x86_64");
    return this;
  }

  /** AWS RHEL host - small instance (t3.medium equivalent). */
  public HostBuilder awsRhelSockets1Cores1() {
    awsRhelDefaults();
    host.sockets(1);
    host.cores(1);
    host.displayName(getOrDefault(host.getDisplayName(), generateName("virtual", "RHEL", 1, 1)));
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

  public HostBuilder inventoryId(UUID inventoryId) {
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

  private String getOrDefault(String value, String defaultValue) {
    return value != null ? value : defaultValue;
  }

  private String getUUIDOfLength(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  private String generateName(
      String infrastType, String instanceType, int socketCount, int coreCount) {
    String prefix =
        StringUtils.capitalize(infrastType.substring(0, Math.min(3, infrastType.length())));
    return String.format(
        "%s-%s-sockets%d-cores%d-%s",
        prefix, instanceType, socketCount, coreCount, getUUIDOfLength(5));
  }
}
