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
package org.candlepin.subscriptions.jobs;

import lombok.Getter;
import lombok.Setter;

/** Contains the configuration properties for all jobs. */
@Getter
@Setter
public class JobProperties {
  // Every hour on the hour
  private String captureHourlySnapshotSchedule = "0 0 * * * ?";

  private String captureSnapshotSchedule = "0 5 * * * ?";

  // Once a day at 3am
  private String purgeSnapshotSchedule = "0 0 3 * * ?";

  public String getCaptureHourlySnapshotSchedule() {
    return captureHourlySnapshotSchedule;
  }

  public void setCaptureHourlySnapshotSchedule(String captureHourlySnapshotSchedule) {
    this.captureHourlySnapshotSchedule = captureHourlySnapshotSchedule;
  }
}
