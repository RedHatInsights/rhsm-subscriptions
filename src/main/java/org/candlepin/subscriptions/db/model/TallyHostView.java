/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.utilization.api.model.Host;

import java.time.OffsetDateTime;

/**
 * A data model around Host and TallyHostBuckets necessary to give us a view of the data to
 * be returned in the Hosts API.
 */
public interface TallyHostView {

    String getInventoryId();
    String getInsightsId();
    String getDisplayName();
    String getHardwareMeasurementType();
    String getHardwareType();
    int getCores();
    int getSockets();
    Integer getNumOfGuests();
    String getSubscriptionManagerId();
    OffsetDateTime getLastSeen();
    boolean isUnmappedGuest();
    boolean isHypervisor();
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
            .numberOfGuests(getNumOfGuests())
            .lastSeen(getLastSeen())
            .isHypervisor(isHypervisor())
            .isUnmappedGuest(isUnmappedGuest())
            .cloudProvider(getCloudProvider());
    }
}
