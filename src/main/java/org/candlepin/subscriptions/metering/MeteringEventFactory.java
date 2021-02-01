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

import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Provides a means to create instances of various types of Event objects
 * for various metrics.
 */
public class MeteringEventFactory {

    private static final Logger log = LoggerFactory.getLogger(MeteringEventFactory.class);

    public static final String OPENSHIFT_CLUSTER_EVENT_SOURCE = "prometheus-openshift";
    public static final String OPENSHIFT_CLUSTER_EVENT_TYPE = "snapshot";
    public static final String OPENSHIFT_CLUSTER_SERVICE_TYPE = "OpenShift Cluster";

    private MeteringEventFactory() {
        throw new IllegalStateException("Utility class; should never be instantiated!");
    }

    /**
     * Creates an Event object that represents a cores snapshot for a given OpenShift cluster.
     *
     * @param accountNumber the account number.
     * @param clusterId the ID of the cluster that was measured.
     * @param serviceLevel the service level of the cluster.
     * @param measuredTime the time the measurement was taken.
     * @param measuredValue the value that was measured./
     * @return a populated Event instance.
     */
    public static Event openShiftClusterCores(String accountNumber, String clusterId, String serviceLevel,
        OffsetDateTime measuredTime, Double measuredValue) {
        Event e = new Event()
            .withEventSource(OPENSHIFT_CLUSTER_EVENT_SOURCE)
            .withEventType(OPENSHIFT_CLUSTER_EVENT_TYPE)
            .withServiceType(OPENSHIFT_CLUSTER_SERVICE_TYPE)
            .withAccountNumber(accountNumber)
            .withInstanceId(clusterId)
            .withTimestamp(measuredTime)
            .withDisplayName(Optional.of(clusterId))
            .withUsage(Usage.PRODUCTION) // Inferred
            .withMeasurements(Arrays.asList(new Measurement().withUom(Uom.CORES).withValue(measuredValue)));

        // NOTE: Prometheus is currently reporting an SLA of Eval. What happens in this case?
        //       Should the event even get sent? Should it be sent so that we can account for
        //       it on the tally side when we create the cluster host records?
        try {
            String sla = serviceLevel == null ? "" : serviceLevel;
            e.setSla(Sla.fromValue(StringUtils.trimWhitespace(sla)));
        }
        catch (IllegalArgumentException iae) {
            log.warn("Unsupported SLA specified for prometheus-openshift event for account {}: {}",
                accountNumber, serviceLevel);
            e.setSla(Sla.__EMPTY__);
        }
        return e;
    }
}
