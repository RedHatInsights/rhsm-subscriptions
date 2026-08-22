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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Host data container with fluent setters.
 *
 * <p>Represents host data that will be seeded into the HBI database. Fields align with the
 * InventoryHost query to support different tally normalization scenarios.
 *
 * <p>Usage:
 *
 * <pre>
 * Host baseHost = new Host(orgId)
 *     .inventoryId("inv-123")
 *     .cores(4)
 *     .sockets(2);
 *
 * SeededHost seeded = hostManager.qpc(baseHost)
 *     .awsRhelLarge()
 *     .displayName("Custom")
 *     .insert();
 * </pre>
 */
public class Host {

  // ===== Core Identity =====
  private String orgId;
  private UUID inventoryId;
  private String subscriptionManagerId;
  private String insightsId;
  private String displayName;

  // ===== System Profile Fields =====
  private String infrastructureType; // "physical" or "virtual"
  private Integer coresPerSocket;
  private Integer numberOfSockets;
  private Integer numberOfCpus;
  private Integer threadsPerCore;
  private String arch; // e.g., "x86_64"
  private String cloudProvider; // e.g., "aws", "azure", null for physical
  private String providerId; // Cloud provider instance ID
  private Boolean isMarketplace;
  private String virtualHostUuid; // Hypervisor UUID (from system_profiles_static)
  private String hostType; // e.g., "edge" (filtered by query line 153)
  private String account; // Deprecated, but included for completeness

  // ===== Facts (stored as JSONB in HBI) =====
  private Map<String, Object> rhsmFacts = new HashMap<>();
  private Map<String, Object> qpcFacts = new HashMap<>();
  private Map<String, Object> satelliteFacts = new HashMap<>();

  // ===== Reporter Info =====
  private String reporter = "component-test";
  private String[] reporters = new String[] {"component-test"};

  // ===== Timestamps =====
  private OffsetDateTime createdOn;
  private OffsetDateTime modifiedOn;
  private OffsetDateTime lastCheckIn;

  // ===== Additional Fields =====
  private String conversionsActivity;
  private String billingModel;

  /**
   * Create a new Host with the specified organization ID.
   *
   * @param orgId the organization ID (required)
   */
  public Host(String orgId) {
    this.orgId = Objects.requireNonNull(orgId, "orgId is required");
  }

  public Host(Host source) {

    // ===== Core Identity =====
    this.orgId = source.getOrgId();
    this.inventoryId = source.getInventoryId();
    this.subscriptionManagerId = source.getSubscriptionManagerId();
    this.insightsId = source.getInsightsId();
    this.displayName = source.getDisplayName();

    // ===== System Profile Fields =====
    this.infrastructureType = source.getInfrastructureType(); // "physical" or "virtual"
    this.coresPerSocket = source.getCoresPerSocket();
    this.numberOfSockets = source.getSockets();
    this.numberOfCpus = source.getCores();
    this.threadsPerCore = source.getThreadsPerCore();
    this.arch = source.getArch(); // e.g., "x86_64"
    this.cloudProvider = source.getCloudProvider(); // e.g., "aws", "azure", null for physical
    this.providerId = source.getProviderId(); // Cloud provider instance ID
    this.isMarketplace = source.getIsMarketplace();
    this.virtualHostUuid =
        source.getVirtualHostUuid(); // Hypervisor UUID (from system_profiles_static)
    this.hostType = source.getHostType(); // e.g., "edge" (filtered by query line 153)
    this.account = source.getAccount(); // Deprecated, but included for completeness

    // ===== Facts (stored as JSONB in HBI) =====
    this.rhsmFacts = source.getRhsmFacts();
    this.qpcFacts = source.getQpcFacts();
    this.satelliteFacts = source.getSatelliteFacts();

    // ===== Reporter Info =====
    // This made need to be appended to the existing reporters array, not replaced.
    this.reporter = source.getReporter();
    this.reporters = source.getReporters();

    // ===== Timestamps =====
    this.createdOn = source.getCreatedOn();
    this.modifiedOn = source.getModifiedOn();
    this.lastCheckIn = source.getLastCheckIn();

    // ===== Additional Fields =====
    this.conversionsActivity = source.getConversionsActivity();
    this.billingModel = source.getBillingModel();
  }

  // ===== Core Identity Setters =====
  // Do not use this setter unless you have to
  public Host inventoryId(UUID inventoryId) {
    this.inventoryId = inventoryId;
    return this;
  }

  public Host subscriptionManagerId(String subscriptionManagerId) {
    this.subscriptionManagerId = subscriptionManagerId;
    return this;
  }

  public Host insightsId(String insightsId) {
    this.insightsId = insightsId;
    return this;
  }

  public Host displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public Host orgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

  // ===== System Profile Setters =====

  public Host infrastructureType(String infrastructureType) {
    this.infrastructureType = infrastructureType;
    return this;
  }

  public Host coresPerSocket(Integer coresPerSocket) {
    this.coresPerSocket = coresPerSocket;
    return this;
  }

  public Host sockets(Integer numberOfSockets) {
    this.numberOfSockets = numberOfSockets;
    return this;
  }

  public Host cores(Integer numberOfCpus) {
    this.numberOfCpus = numberOfCpus;
    return this;
  }

  public Host threadsPerCore(Integer threadsPerCore) {
    this.threadsPerCore = threadsPerCore;
    return this;
  }

  public Host arch(String arch) {
    this.arch = arch;
    return this;
  }

  public Host cloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
    return this;
  }

  public Host providerId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  public Host isMarketplace(Boolean isMarketplace) {
    this.isMarketplace = isMarketplace;
    return this;
  }

  public Host virtualHostUuid(String virtualHostUuid) {
    this.virtualHostUuid = virtualHostUuid;
    return this;
  }

  public Host hostType(String hostType) {
    this.hostType = hostType;
    return this;
  }

  public Host account(String account) {
    this.account = account;
    return this;
  }

  // ===== Facts Setters =====

  /**
   * Add a RHSM fact (stored in h.facts->'rhsm' in HBI).
   *
   * <p>Common RHSM facts: IS_VIRTUAL, RH_PROD, ARCHITECTURE, CORES, SOCKETS, BILLING_MODEL,
   * SYSPURPOSE_ROLE, SYSPURPOSE_SLA, SYSPURPOSE_USAGE
   */
  public Host rhsmFact(String key, Object value) {
    this.rhsmFacts.put(key, value);
    return this;
  }

  /**
   * Add a QPC fact (stored in h.facts->'qpc' in HBI).
   *
   * <p>Common QPC facts: rh_products_installed, IS_RHEL
   */
  public Host qpcFact(String key, Object value) {
    this.qpcFacts.put(key, value);
    return this;
  }

  /**
   * Add a Satellite fact (stored in h.facts->'satellite' in HBI).
   *
   * <p>Common Satellite facts: virtual_host_uuid, system_purpose_role, system_purpose_sla,
   * system_purpose_usage
   */
  public Host satelliteFact(String key, Object value) {
    this.satelliteFacts.put(key, value);
    return this;
  }

  // ===== Reporter Setters =====

  public Host reporter(String reporter) {
    this.reporter = reporter;
    return this;
  }

  public Host reporters(String... reporters) {
    this.reporters = reporters;
    return this;
  }

  // ===== Timestamp Setters =====

  public Host createdOn(OffsetDateTime createdOn) {
    this.createdOn = createdOn;
    return this;
  }

  public Host modifiedOn(OffsetDateTime modifiedOn) {
    this.modifiedOn = modifiedOn;
    return this;
  }

  public Host lastCheckIn(OffsetDateTime lastCheckIn) {
    this.lastCheckIn = lastCheckIn;
    return this;
  }

  // ===== Additional Field Setters =====

  public Host conversionsActivity(String conversionsActivity) {
    this.conversionsActivity = conversionsActivity;
    return this;
  }

  public Host billingModel(String billingModel) {
    this.billingModel = billingModel;
    return this;
  }

  // ===== Getters =====

  public String getOrgId() {
    return orgId;
  }

  public UUID getInventoryId() {
    return inventoryId;
  }

  public String getSubscriptionManagerId() {
    return subscriptionManagerId;
  }

  public String getInsightsId() {
    return insightsId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getInfrastructureType() {
    return infrastructureType;
  }

  public Integer getCoresPerSocket() {
    return coresPerSocket;
  }

  public Integer getSockets() {
    return numberOfSockets;
  }

  public Integer getCores() {
    return numberOfCpus;
  }

  public Integer getThreadsPerCore() {
    return threadsPerCore;
  }

  public String getArch() {
    return arch;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public String getProviderId() {
    return providerId;
  }

  public Boolean getIsMarketplace() {
    return isMarketplace;
  }

  public String getVirtualHostUuid() {
    return virtualHostUuid;
  }

  public String getHostType() {
    return hostType;
  }

  public String getAccount() {
    return account;
  }

  public Map<String, Object> getRhsmFacts() {
    return rhsmFacts;
  }

  public Map<String, Object> getQpcFacts() {
    return qpcFacts;
  }

  public Map<String, Object> getSatelliteFacts() {
    return satelliteFacts;
  }

  public String getReporter() {
    return reporter;
  }

  public String[] getReporters() {
    return reporters;
  }

  public OffsetDateTime getCreatedOn() {
    return createdOn;
  }

  public OffsetDateTime getModifiedOn() {
    return modifiedOn;
  }

  public OffsetDateTime getLastCheckIn() {
    return lastCheckIn;
  }

  public String getConversionsActivity() {
    return conversionsActivity;
  }

  public String getBillingModel() {
    return billingModel;
  }
}
