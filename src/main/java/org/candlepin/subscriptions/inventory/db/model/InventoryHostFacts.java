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
package org.candlepin.subscriptions.inventory.db.model;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/** Represents an inventory host's facts. */
@Getter
@Setter
public class InventoryHostFacts {
  private UUID inventoryId;
  private OffsetDateTime modifiedOn;
  private String account;
  private String displayName;
  private String orgId;
  private String syncTimestamp;
  private Set<String> products;
  private String systemProfileInfrastructureType;
  private Integer systemProfileCoresPerSocket;
  private Integer systemProfileSockets;
  private Integer systemProfileCpus;
  private Integer systemProfileThreadsPerCore;
  private String systemProfileArch;
  private boolean isMarketplace;
  private boolean conversionsActivity;
  private boolean isVirtual;
  private String hypervisorUuid;
  private String satelliteHypervisorUuid;
  private String satelliteRole;
  private String satelliteSla;
  private String satelliteUsage;
  private String guestId;
  private String subscriptionManagerId;
  private String insightsId;
  private String providerId;
  private Set<String> qpcProducts;
  private Set<String> systemProfileProductIds;
  private String syspurposeRole;
  private String syspurposeSla;
  private String syspurposeUsage;
  private String syspurposeUnits;
  private String billingModel;
  private String cloudProvider;
  private OffsetDateTime staleTimestamp;
  private String hardwareSubmanId;

  public InventoryHostFacts() {
    // Used for testing
  }

  /**
   * Constructor represent current query structure for reads from HBI Must be consistent with
   * InventoryHost (Any changes here must be made in this Object. vice versa)
   */
  @SuppressWarnings("squid:S00107")
  public InventoryHostFacts(
      UUID inventoryId,
      OffsetDateTime modifiedOn,
      String account,
      String displayName,
      String orgId,
      String products,
      String syncTimestamp,
      String systemProfileInfrastructureType,
      Integer systemProfileCoresPerSocket,
      Integer systemProfileSockets,
      Integer systemProfileCpus,
      Integer systemProfileThreadsPerCore,
      String systemProfileArch,
      Boolean isMarketplace,
      String conversionsActivity,
      String qpcProducts,
      String systemProfileProductIds,
      String syspurposeRole,
      String syspurposeSla,
      String syspurposeUsage,
      String syspurposeUnits,
      String billingModel,
      String isVirtual,
      String hypervisorUuid,
      String satelliteHypervisorUuid,
      String satelliteRole,
      String satelliteSla,
      String satelliteUsage,
      String guestId,
      String subscriptionManagerId,
      String insightsId,
      String providerId,
      String cloudProvider,
      OffsetDateTime staleTimestamp,
      String hardwareSubmanId) {
    this.inventoryId = inventoryId;
    this.modifiedOn = modifiedOn;
    this.account = account;
    this.displayName = displayName;
    this.orgId = orgId;
    this.products = asStringSet(products);
    this.qpcProducts = asStringSet(qpcProducts);
    this.syncTimestamp = StringUtils.hasText(syncTimestamp) ? syncTimestamp : "";
    this.systemProfileInfrastructureType = systemProfileInfrastructureType;
    this.systemProfileCoresPerSocket = nullToZero(systemProfileCoresPerSocket);
    this.systemProfileSockets = nullToZero(systemProfileSockets);
    this.systemProfileCpus = nullToZero(systemProfileCpus);
    this.systemProfileThreadsPerCore = nullToZero(systemProfileThreadsPerCore);
    this.systemProfileArch = systemProfileArch;
    this.isMarketplace = nullToFalse(isMarketplace);
    this.conversionsActivity = asBoolean(conversionsActivity);
    this.systemProfileProductIds = asStringSet(systemProfileProductIds);
    this.syspurposeRole = syspurposeRole;
    this.syspurposeSla = syspurposeSla;
    this.syspurposeUsage = syspurposeUsage;
    this.syspurposeUnits = syspurposeUnits;
    this.isVirtual = asBoolean(isVirtual);
    this.hypervisorUuid = hypervisorUuid;
    this.satelliteHypervisorUuid = satelliteHypervisorUuid;
    this.satelliteRole = satelliteRole;
    this.satelliteSla = satelliteSla;
    this.satelliteUsage = satelliteUsage;
    this.guestId = guestId;
    this.subscriptionManagerId = subscriptionManagerId;
    this.insightsId = insightsId;
    this.providerId = providerId;
    this.billingModel = billingModel;
    this.cloudProvider = cloudProvider;
    this.staleTimestamp = staleTimestamp;
    this.hardwareSubmanId = hardwareSubmanId;
  }

  public void setProducts(String products) {
    this.products = asStringSet(products);
  }

  private boolean asBoolean(String value) {
    return Boolean.parseBoolean(value);
  }

  private boolean nullToFalse(Boolean value) {
    return value != null && value;
  }

  private Integer nullToZero(Integer value) {
    return value == null ? 0 : value;
  }

  private Set<String> asStringSet(String productJson) {
    if (!StringUtils.hasText(productJson)) {
      return new HashSet<>();
    }
    return StringUtils.commaDelimitedListToSet(productJson);
  }

  public Integer getSystemProfileCoresPerSocket() {
    return systemProfileCoresPerSocket == null ? 0 : systemProfileCoresPerSocket;
  }

  public Integer getSystemProfileSockets() {
    return systemProfileSockets == null ? 0 : systemProfileSockets;
  }

  public void setQpcProducts(String qpcProducts) {
    this.qpcProducts = asStringSet(qpcProducts);
  }

  public void setSystemProfileProductIds(String productIds) {
    this.systemProfileProductIds = asStringSet(productIds);
  }
}
