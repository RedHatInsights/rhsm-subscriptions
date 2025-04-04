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
package com.redhat.swatch.hbi.domain.normalization.facts;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.json.Event.CloudProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;

@Builder
@Getter
public class NormalizedFacts {

  // Identification Fields
  private String orgId;
  private String inventoryId;
  private String instanceId;
  private String insightsId;
  private String subscriptionManagerId;

  // Metadata Fields
  private String displayName;
  private OffsetDateTime lastSeen;
  private String lastSyncTimestamp; // Renamed from syncTimestamp for better clarity

  // Usage and SLA Information
  private String usage;
  private String sla;

  // Flags and Booleans
  private boolean is3rdPartyMigrated;
  private boolean isVirtual;
  private boolean isHypervisor;
  private boolean isUnmappedGuest;

  // Relationships and Complex Objects
  private HardwareMeasurementType cloudProviderType;
  private CloudProvider cloudProvider;
  private String hypervisorUuid;
  private Set<String> productTags;
  private Set<String> productIds;
  private HardwareType hardwareType;

  // Constants
  private static final boolean IS_VALID_STRING = true; // Improves readability in validation logic

  /**
   * Determines if this instance is a virtual guest.
   *
   * @return true if the instance is virtual and has a valid hypervisor UUID.
   */
  public boolean isGuest() {
    return isVirtual() && hasValidHypervisorUuid();
  }

  /**
   * Checks whether the hypervisor UUID is valid (non-empty).
   *
   * @return true if the hypervisor UUID is valid, false otherwise.
   */
  private boolean hasValidHypervisorUuid() {
    return StringUtils.isNotEmpty(hypervisorUuid) == IS_VALID_STRING;
  }
}
