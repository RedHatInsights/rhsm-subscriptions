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
package com.redhat.swatch.hbi.events.normalization.facts;

import com.redhat.swatch.hbi.events.normalization.NormalizedMeasurements;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.json.Event.CloudProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;

@Builder
@Getter
public class HostFacts {

  private String orgId;
  private String inventoryId;
  private String instanceId;
  private String insightsId;
  private String subscriptionManagerId;
  private String displayName;
  private String usage;
  private String sla;
  private boolean is3rdPartyMigrated;
  private HardwareMeasurementType cloudProviderType;
  private CloudProvider cloudProvider;
  private String syncTimestamp;
  private boolean isVirtual;
  private String hypervisorUuid;
  private Set<String> productTags;
  private Set<String> productIds;
  private HardwareType hardwareType;

  @Setter private NormalizedMeasurements measurements;
}
