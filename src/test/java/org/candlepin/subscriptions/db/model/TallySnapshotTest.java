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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.json.Measurement.Uom;
import org.junit.jupiter.api.Test;

class TallySnapshotTest {

  @Test
  void testShouldIgnoreHbiAwsWhenCloudigradeAwsPresent() {
    TallySnapshot snapshot = new TallySnapshot();
    HardwareMeasurement hbiMeasurement = new HardwareMeasurement();
    hbiMeasurement.setSockets(3);
    hbiMeasurement.setInstanceCount(3);
    HardwareMeasurement cloudigradeMeasurement = new HardwareMeasurement();
    cloudigradeMeasurement.setSockets(7);
    cloudigradeMeasurement.setInstanceCount(7);
    snapshot.setHardwareMeasurement(HardwareMeasurementType.AWS, hbiMeasurement);
    snapshot.setHardwareMeasurement(
        HardwareMeasurementType.AWS_CLOUDIGRADE, cloudigradeMeasurement);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(7, apiSnapshot.getCloudInstanceCount().intValue());
    assertEquals(7, apiSnapshot.getCloudSockets().intValue());
    assertTrue(apiSnapshot.getHasCloudigradeData());
    assertTrue(apiSnapshot.getHasCloudigradeMismatch());
  }

  @Test
  void testShouldNotFlagCloudigradeDataIfNotPresent() {
    TallySnapshot snapshot = new TallySnapshot();
    HardwareMeasurement hbiMeasurement = new HardwareMeasurement();
    hbiMeasurement.setSockets(3);
    hbiMeasurement.setInstanceCount(3);
    snapshot.setHardwareMeasurement(HardwareMeasurementType.AWS, hbiMeasurement);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(3, apiSnapshot.getCloudInstanceCount().intValue());
    assertEquals(3, apiSnapshot.getCloudSockets().intValue());
    assertFalse(apiSnapshot.getHasCloudigradeData());
    assertFalse(apiSnapshot.getHasCloudigradeMismatch());
  }

  @Test
  void testShouldNotFlagCloudigradeMismatchIfMatching() {
    TallySnapshot snapshot = new TallySnapshot();
    HardwareMeasurement hbiMeasurement = new HardwareMeasurement();
    hbiMeasurement.setSockets(7);
    hbiMeasurement.setInstanceCount(7);
    HardwareMeasurement cloudigradeMeasurement = new HardwareMeasurement();
    cloudigradeMeasurement.setSockets(7);
    cloudigradeMeasurement.setInstanceCount(7);
    snapshot.setHardwareMeasurement(HardwareMeasurementType.AWS, hbiMeasurement);
    snapshot.setHardwareMeasurement(
        HardwareMeasurementType.AWS_CLOUDIGRADE, cloudigradeMeasurement);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(7, apiSnapshot.getCloudInstanceCount().intValue());
    assertEquals(7, apiSnapshot.getCloudSockets().intValue());
    assertTrue(apiSnapshot.getHasCloudigradeData());
    assertFalse(apiSnapshot.getHasCloudigradeMismatch());
  }

  @Test
  void testShouldTolerateAccountWithOnlyCloudigrade() {
    TallySnapshot snapshot = new TallySnapshot();
    HardwareMeasurement hbiMeasurement = new HardwareMeasurement();
    hbiMeasurement.setSockets(7);
    hbiMeasurement.setInstanceCount(7);
    HardwareMeasurement cloudigradeMeasurement = new HardwareMeasurement();
    cloudigradeMeasurement.setSockets(7);
    cloudigradeMeasurement.setInstanceCount(7);
    snapshot.setHardwareMeasurement(
        HardwareMeasurementType.AWS_CLOUDIGRADE, cloudigradeMeasurement);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(7, apiSnapshot.getCloudInstanceCount().intValue());
    assertEquals(7, apiSnapshot.getCloudSockets().intValue());
    assertTrue(apiSnapshot.getHasCloudigradeData());
    assertFalse(apiSnapshot.getHasCloudigradeMismatch());
  }

  @Test
  void testShouldAddHypervisorAndVirtual() {
    TallySnapshot snapshot = new TallySnapshot();
    HardwareMeasurement hypervisorMeasurement = new HardwareMeasurement();
    hypervisorMeasurement.setSockets(30);
    hypervisorMeasurement.setInstanceCount(3);
    HardwareMeasurement virtualMeasurement = new HardwareMeasurement();
    virtualMeasurement.setSockets(70);
    virtualMeasurement.setInstanceCount(7);
    snapshot.setHardwareMeasurement(HardwareMeasurementType.HYPERVISOR, hypervisorMeasurement);
    snapshot.setHardwareMeasurement(HardwareMeasurementType.VIRTUAL, virtualMeasurement);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();

    assertEquals(10, apiSnapshot.getHypervisorInstanceCount().intValue());
    assertEquals(100, apiSnapshot.getHypervisorSockets().intValue());
  }

  @Test
  void shouldAddCoreHoursWhenCreatingApiSnapshot() {
    Double expCoreHours = 22.2;
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, expCoreHours);

    org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot =
        snapshot.asApiSnapshot();
    assertEquals(expCoreHours, apiSnapshot.getCoreHours());
  }
}
