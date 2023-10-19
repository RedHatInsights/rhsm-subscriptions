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
package org.candlepin.subscriptions.tally.filler;

import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;

public class TallySnapshotAdapter implements ReportFillerAdapter<TallySnapshot> {

  private final ApplicationClock clock;

  public TallySnapshotAdapter(ApplicationClock clock) {
    this.clock = clock;
  }

  @Override
  public boolean itemIsLarger(TallySnapshot oldSnap, TallySnapshot newSnap) {
    return newSnap.getInstanceCount() > oldSnap.getInstanceCount()
        || newSnap.getCores() > oldSnap.getCores()
        || newSnap.getSockets() > oldSnap.getSockets();
  }

  @Override
  public TallySnapshot createDefaultItem(
      OffsetDateTime itemDate, TallySnapshot previous, boolean useRunningTotalFormat) {
    if (itemDate.isBefore(clock.now()) && useRunningTotalFormat && previous != null) {
      return new TallySnapshot()
          .date(itemDate)
          .cores(previous.getCores())
          .sockets(previous.getSockets())
          .instanceCount(previous.getInstanceCount())
          .physicalSockets(previous.getPhysicalSockets())
          .physicalCores(previous.getPhysicalCores())
          .physicalInstanceCount(previous.getPhysicalInstanceCount())
          .hypervisorSockets(previous.getHypervisorSockets())
          .hypervisorCores(previous.getHypervisorCores())
          .hypervisorInstanceCount(previous.getHypervisorInstanceCount())
          .cloudInstanceCount(previous.getCloudInstanceCount())
          .cloudSockets(previous.getCloudSockets())
          .cloudCores(previous.getCloudCores())
          .coreHours(previous.getCoreHours())
          .hasData(
              true); // has_data = true means that the frontend should show the value in a tooltip
    }
    Integer defaultValueInteger;
    Double defaultValue;
    if (itemDate.isBefore(clock.now())) {
      defaultValueInteger = 0;
      defaultValue = 0.0;
    } else {
      defaultValueInteger = null;
      defaultValue = null;
    }
    return new TallySnapshot()
        .date(itemDate)
        .cores(defaultValueInteger)
        .sockets(defaultValueInteger)
        .instanceCount(defaultValueInteger)
        .physicalSockets(defaultValueInteger)
        .physicalCores(defaultValueInteger)
        .physicalInstanceCount(defaultValueInteger)
        .hypervisorSockets(defaultValueInteger)
        .hypervisorCores(defaultValueInteger)
        .hypervisorInstanceCount(defaultValueInteger)
        .cloudInstanceCount(defaultValueInteger)
        .cloudSockets(defaultValueInteger)
        .cloudCores(defaultValueInteger)
        .coreHours(defaultValue)
        .hasData(false);
  }

  @Override
  public OffsetDateTime getDate(TallySnapshot item) {
    return item.getDate();
  }
}
