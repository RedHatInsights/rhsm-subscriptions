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
package org.candlepin.subscriptions.metering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;

class MeteringEventFactoryTest {

  @Test
  void testOpenShiftClusterCoresEventCreation() throws Exception {
    String account = "my-account";
    String clusterId = "my-cluster";
    String sla = "Premium";
    String usage = "Production";
    String role = "ocp";
    OffsetDateTime expiry = OffsetDateTime.now();
    OffsetDateTime measuredTime = expiry.minusHours(1);
    Double measuredValue = 23.0;

    Event event =
        MeteringEventFactory.openShiftClusterCores(
            account, clusterId, sla, usage, role, measuredTime, expiry, measuredValue);
    assertEquals(account, event.getAccountNumber());
    assertEquals(measuredTime, event.getTimestamp());
    assertEquals(expiry, event.getExpiration().get());
    assertEquals(clusterId, event.getInstanceId());
    assertEquals(Optional.of(clusterId), event.getDisplayName());
    assertEquals(Sla.PREMIUM, event.getSla());
    assertEquals(Usage.PRODUCTION, event.getUsage());
    assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_SOURCE, event.getEventSource());
    assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_TYPE, event.getEventType());
    assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_SERVICE_TYPE, event.getServiceType());
    assertEquals(1, event.getMeasurements().size());
    Measurement measurement = event.getMeasurements().get(0);
    assertEquals(Measurement.Uom.CORES, measurement.getUom());
    assertEquals(measuredValue, measurement.getValue());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullServiceLevel() throws Exception {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            null,
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresSlaSetToEmptyForSlaValueNone() throws Exception {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "None",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertEquals(Sla.__EMPTY__, event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresInvalidSlaWillNotBeSetOnEvent() throws Exception {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "UNKNOWN_SLA",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresInvalidUsageSetsNullValue() throws Exception {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "Premium",
            "UNKNOWN_USAGE",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getUsage());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullUsage() throws Exception {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "Premium",
            null,
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getUsage());
  }

  @Test
  void testOpenShiftClusterCoresInvalidRoleSetsNullValue() {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "Premium",
            "Production",
            "UNKNOWN_ROLE",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getRole());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullRole() {
    Event event =
        MeteringEventFactory.openShiftClusterCores(
            "my-account",
            "cluster-id",
            "Premium",
            "Production",
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            12.5);
    assertNull(event.getRole());
  }
}
