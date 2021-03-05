/*
 * Copyright (c) 2021 Red Hat, Inc.
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
import java.util.List;
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
        String usage, OffsetDateTime measuredTime, OffsetDateTime expired, Double measuredValue) {
        Event event = new Event();
        updateOpenShiftClusterCores(event, accountNumber, clusterId, serviceLevel, usage,
            measuredTime, expired, measuredValue);
        return event;
    }

    @SuppressWarnings("java:S107")
    public static void updateOpenShiftClusterCores(Event toUpdate, String accountNumber, String clusterId,
        String serviceLevel, String usage, OffsetDateTime measuredTime, OffsetDateTime expired,
        Double measuredValue) {
        toUpdate
            .withEventSource(OPENSHIFT_CLUSTER_EVENT_SOURCE)
            .withEventType(OPENSHIFT_CLUSTER_EVENT_TYPE)
            .withServiceType(OPENSHIFT_CLUSTER_SERVICE_TYPE)
            .withAccountNumber(accountNumber)
            .withInstanceId(clusterId)
            .withTimestamp(measuredTime)
            .withExpiration(Optional.of(expired))
            .withDisplayName(Optional.of(clusterId))
            .withSla(getSla(serviceLevel, accountNumber, clusterId))
            .withUsage(getUsage(usage, accountNumber, clusterId))
            .withMeasurements(List.of(new Measurement().withUom(Uom.CORES).withValue(measuredValue)));
    }

    private static Sla getSla(String serviceLevel, String account, String clusterId) {
        /**
         * SLA values set by OCM:
         *   - Eval (ignored for now)
         *   - Standard
         *   - Premium
         *   - Self-Support
         *   - None (converted to be __EMPTY__)
         */
        try {
            String sla = "None".equalsIgnoreCase(serviceLevel) ? "" : serviceLevel;
            return Sla.fromValue(StringUtils.trimWhitespace(sla));
        }
        catch (IllegalArgumentException e) {
            log.warn("Unsupported SLA '{}' specified for event. account/cluster: {}/{}",
                serviceLevel, account, clusterId);
        }
        return null;
    }

    private static Usage getUsage(String usage, String account, String clusterId) {
        if (usage == null) {
            return null;
        }

        try {
            return Usage.fromValue(StringUtils.trimWhitespace(usage));
        }
        catch (IllegalArgumentException e) {
            log.warn("Unsupported Usage '{}' specified for event. account/cluster: {}/{}",
                usage, account, clusterId);
        }
        return null;
    }
}
