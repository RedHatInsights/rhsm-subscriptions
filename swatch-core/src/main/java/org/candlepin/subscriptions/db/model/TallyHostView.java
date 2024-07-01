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
import org.candlepin.subscriptions.utilization.api.v1.model.Host;
import org.springframework.beans.factory.annotation.Value;

/**
 * A data projection around Host and TallyHostBuckets necessary to give us a view of the data to be
 * returned in the Hosts API.
 */
public interface TallyHostView {

  @Value("#{target.host.inventoryId}")
  String getInventoryId();

  @Value("#{target.host.insightsId}")
  String getInsightsId();

  @Value("#{target.host.displayName}")
  String getDisplayName();

  @Value("#{target.measurementType}")
  String getHardwareMeasurementType();

  @Value("#{target.host.hardwareType}")
  String getHardwareType();

  @Value("#{target.cores}")
  int getCores();

  @Value("#{target.sockets}")
  int getSockets();

  @Value("#{target.host.numOfGuests}")
  Integer getNumberOfGuests();

  @Value("#{target.host.subscriptionManagerId}")
  String getSubscriptionManagerId();

  @Value("#{target.host.lastSeen}")
  OffsetDateTime getLastSeen();

  @Value("#{target.host.unmappedGuest}")
  boolean isUnmappedGuest();

  @Value("#{target.host.hypervisor}")
  boolean isHypervisor();

  @Value("#{target.host.cloudProvider}")
  String getCloudProvider();

  default Host asApiHost() {
    return new Host()
        .inventoryId(getInventoryId())
        .insightsId(getInsightsId())
        .hardwareType(getHardwareType())
        .measurementType(getHardwareMeasurementType())
        .cores(getCores())
        .sockets(getSockets())
        .displayName(getDisplayName())
        .subscriptionManagerId(getSubscriptionManagerId())
        .numberOfGuests(getNumberOfGuests())
        .lastSeen(getLastSeen())
        .isHypervisor(isHypervisor())
        .isUnmappedGuest(isUnmappedGuest())
        .cloudProvider(getCloudProvider());
  }
}
