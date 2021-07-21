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
package org.candlepin.subscriptions.util;

import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.utilization.api.model.Host;

//TODO make this a @Service or @Component that can be autowired
public class HostEntityToPojoConverter {

  public Host asApiPojo(org.candlepin.subscriptions.db.model.Host host) {

     return new Host()
         .cores(host.getCores())
         .sockets(host.getSockets())
         .displayName(host.getDisplayName())
         .insightsId(host.getInsightsId())
         .inventoryId(host.getInventoryId())
         .lastSeen(host.getLastSeen())
         .numberOfGuests(host.getNumOfGuests())
         .isUnmappedGuest(host.isUnmappedGuest())
         .isHypervisor(host.isHypervisor())
         .cloudProvider(host.getCloudProvider())
         .hardwareType(host.getHardwareType().toString()) //TODO null protect
         .subscriptionManagerId(host.getSubscriptionManagerId());

  }

  public Host asApiHost(TallyHostView tallyHostView) {
    return new Host()
    .inventoryId(tallyHostView.getInventoryId())
    .insightsId(tallyHostView.getInsightsId())
        .hardwareType(tallyHostView.getHardwareType())
        .measurementType(tallyHostView.getHardwareMeasurementType())
        .cores(tallyHostView.getCores())
        .sockets(tallyHostView.getSockets())
        .displayName(tallyHostView.getDisplayName())
        .subscriptionManagerId(tallyHostView.getSubscriptionManagerId())
        .numberOfGuests(tallyHostView.getNumberOfGuests())
        .lastSeen(tallyHostView.getLastSeen())
        .isHypervisor(tallyHostView.isHypervisor())
        .isUnmappedGuest(tallyHostView.isUnmappedGuest())
        .cloudProvider(tallyHostView.getCloudProvider());
  }

    // TODO(khowell): move elsewhere
  //  public org.candlepin.subscriptions.utilization.api.model.Host asTallyHostViewApiHost(
  //      String monthId) {
  //    var host = new org.candlepin.subscriptions.utilization.api.model.Host();
  //
  //    host.inventoryId(getInventoryId());
  //    host.insightsId(getInsightsId());
  //
  //    host.hardwareType(
  //        Objects.requireNonNullElse(getHardwareType(), HostHardwareType.PHYSICAL).toString());
  //    host.cores(Objects.requireNonNullElse(getMeasurement(Measurement.Uom.CORES),
  // 0.0).intValue());
  //    host.sockets(
  //        Objects.requireNonNullElse(getMeasurement(Measurement.Uom.SOCKETS), 0.0).intValue());
  //
  //    host.displayName(getDisplayName());
  //    host.subscriptionManagerId(getSubscriptionManagerId());
  //    host.numberOfGuests(getNumOfGuests());
  //    host.lastSeen(getLastSeen());
  //    host.isUnmappedGuest(isUnmappedGuest());
  //    host.cloudProvider(getCloudProvider());
  //
  //    // These generally come off of the TallyHostBuckets, but it's different for the
  //    // OpenShift-metrics
  //    // and OpenShift-dedicated-metrics products, since they're not using the deprecated unit of
  //    // measure
  //    // model.  Note there's no asHypervisor here either.
  //
  //    host.isHypervisor(isHypervisor());
  //
  //    HardwareMeasurementType measurementType =
  //        buckets.stream().findFirst().orElseThrow().getMeasurementType();
  //
  //    host.measurementType(
  //        Objects.requireNonNullElse(measurementType,
  // HardwareMeasurementType.PHYSICAL).toString());
  //
  //    // Core Hours is currently only applicable to the OpenShift-metrics
  // OpenShift-dedicated-metrics
  //    // ProductIDs, and the UI is only query the host api in one month timeframes.  If the
  //    // granularity of that API changes in the future, other work will have to be done first to
  //    // capture relationships between hosts & snapshots to derive coreHours within dynamic
  // timeframes
  //
  //    host.coreHours(getMonthlyTotal(monthId, Uom.CORES));
  //
  //    return host;
  //  }

}