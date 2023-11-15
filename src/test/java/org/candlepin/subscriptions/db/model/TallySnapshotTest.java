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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import org.junit.jupiter.api.Test;

class TallySnapshotTest {

  @Test
  void testShouldAddHypervisorAndVirtual() {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setMeasurement(HardwareMeasurementType.HYPERVISOR, MetricIdUtils.getSockets(), 30.0);
    snapshot.setMeasurement(
        HardwareMeasurementType.HYPERVISOR, MetricIdUtils.getInstanceHours(), 3.0);
    snapshot.setMeasurement(HardwareMeasurementType.VIRTUAL, MetricIdUtils.getSockets(), 70.0);
    snapshot.setMeasurement(HardwareMeasurementType.VIRTUAL, MetricIdUtils.getInstanceHours(), 7.0);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(10, apiSnapshot.getHypervisorInstanceCount().intValue());
    assertEquals(100, apiSnapshot.getHypervisorSockets().intValue());
  }

  @Test
  void shouldAddCoreHoursWhenCreatingApiSnapshot() {
    Double expCoreHours = 22.2;
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), expCoreHours);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();
    assertEquals(expCoreHours, apiSnapshot.getCoreHours());
  }

  @Test
  void shouldAddInstanceHoursWhenCreatingApiSnapshot() {
    Double expCoreHours = 22.2;
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setMeasurement(
        HardwareMeasurementType.TOTAL, MetricIdUtils.getInstanceHours(), expCoreHours);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();
    assertEquals(expCoreHours, apiSnapshot.getInstanceHours());
  }
}
