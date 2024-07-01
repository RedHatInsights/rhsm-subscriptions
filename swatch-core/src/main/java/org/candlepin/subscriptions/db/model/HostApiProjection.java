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
package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HostApiProjection {

  private String inventoryId;
  private String insightsId;
  private String displayName;
  private String subscriptionManagerId;
  private Double sockets;
  private Double cores;
  private Double coreHours;
  private Double instanceHours;
  private HostHardwareType hardwareType;
  private HardwareMeasurementType measurementType;
  private Integer numberOfGuests;
  private OffsetDateTime lastSeen;
  private Boolean isUnmappedGuest;
  private Boolean isHypervisor;
  private String cloudProvider;
  private BillingProvider billingProvider;
  private String billingAccountId;

  public org.candlepin.subscriptions.utilization.api.v1.model.Host asTallyHostViewApiHost() {
    var host = new org.candlepin.subscriptions.utilization.api.v1.model.Host();

    host.inventoryId(getInventoryId());
    host.insightsId(getInsightsId());

    host.hardwareType(
        Objects.requireNonNullElse(getHardwareType(), HostHardwareType.PHYSICAL).toString());
    host.cores(Objects.requireNonNullElse(cores, 0.0).intValue());
    host.sockets(Objects.requireNonNullElse(sockets, 0.0).intValue());

    host.displayName(getDisplayName());
    host.subscriptionManagerId(getSubscriptionManagerId());
    host.numberOfGuests(getNumberOfGuests());
    host.lastSeen(getLastSeen());
    host.isUnmappedGuest(getIsUnmappedGuest());
    host.cloudProvider(getCloudProvider());

    // These generally come off of the TallyHostBuckets, but it's different for the
    // OpenShift-metrics
    // and OpenShift-dedicated-metrics products, since they're not using the deprecated unit of
    // measure
    // model.  Note there's no asHypervisor here either.
    host.isHypervisor(getIsHypervisor());

    host.measurementType(
        Objects.requireNonNullElse(getMeasurementType(), HardwareMeasurementType.PHYSICAL)
            .toString());

    // Core Hours is currently only applicable to the OpenShift-metrics OpenShift-dedicated-metrics
    // ProductIDs, and the UI is only query the host api in one month timeframes.  If the
    // granularity of that API changes in the future, other work will have to be done first to
    // capture relationships between hosts & snapshots to derive coreHours within dynamic timeframes

    host.coreHours(coreHours);
    host.instanceHours(instanceHours);

    return host;
  }
}
