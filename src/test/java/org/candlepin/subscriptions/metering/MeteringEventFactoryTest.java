/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Measurement;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

class MeteringEventFactoryTest {

    @Test
    void testOpenShiftClusterEventCreation() throws Exception {
        String account = "my-account";
        String clusterId = "my-cluster";
        String sla = "Premium";
        OffsetDateTime measuredTime = OffsetDateTime.now();
        Double measuredValue = 23.0;

        Event event = MeteringEventFactory.openShiftClusterCores(account, clusterId, sla, measuredTime,
            measuredValue);
        assertEquals(account, event.getAccountNumber());
        assertEquals(measuredTime, event.getTimestamp());
        assertEquals(clusterId, event.getInstanceId());
        assertEquals(Optional.of(clusterId), event.getDisplayName());
        assertEquals(Sla.PREMIUM, event.getSla());
        assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_SOURCE, event.getEventSource());
        assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_TYPE, event.getEventType());
        assertEquals(MeteringEventFactory.OPENSHIFT_CLUSTER_SERVICE_TYPE, event.getServiceType());
        assertEquals(1, event.getMeasurements().size());
        Measurement measurement = event.getMeasurements().get(0);
        assertEquals(Measurement.Uom.CORES, measurement.getUom());
        assertEquals(measuredValue, measurement.getValue());
    }

    @Test
    void testOpenShiftClusterEventHandlesNullServiceLevel() throws Exception {
        Event event = MeteringEventFactory.openShiftClusterCores("my-account", "cluster-id", null,
            OffsetDateTime.now(), 12.5);
        assertEquals(Sla.__EMPTY__, event.getSla());
    }

    @Test
    void testOpenShiftClusterEventSlaSetToEmptyForSlaValueNone() throws Exception {
        Event event = MeteringEventFactory.openShiftClusterCores("my-account", "cluster-id", "None",
            OffsetDateTime.now(), 12.5);
        assertEquals(Sla.__EMPTY__, event.getSla());
    }

    @Test
    void testInvalidSlaCausesExceptionDuringOpenShiftClusterEventCreation() throws Exception {
        Throwable e = assertThrows(EventCreationException.class, () -> {
            MeteringEventFactory.openShiftClusterCores("my-account", "cluster-id", "UNKNOWN_SLA",
                OffsetDateTime.now(), 12.5);
        });
        assertEquals("Unsupported SLA 'UNKNOWN_SLA' specified for event. account/cluster: " +
            "my-account/cluster-id", e.getMessage());
    }

}
