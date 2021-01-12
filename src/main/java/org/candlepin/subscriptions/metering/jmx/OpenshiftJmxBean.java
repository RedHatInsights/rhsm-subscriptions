/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.jmx;

import org.candlepin.subscriptions.metering.service.prometheus.PrometheusServicePropeties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

/**
 * Exposes the ability to trigger metering operations from JMX.
 */
@ManagedResource
public class OpenshiftJmxBean {

    private static final Logger log = LoggerFactory.getLogger(OpenshiftJmxBean.class);

    private PrometheusMetricsTaskManager tasks;

    private ApplicationClock clock;

    private PrometheusServicePropeties servicePropeties;

    @Autowired
    public OpenshiftJmxBean(ApplicationClock clock, PrometheusMetricsTaskManager tasks,
        PrometheusServicePropeties servicePropeties) {
        this.clock = clock;
        this.tasks = tasks;
        this.servicePropeties = servicePropeties;
    }

    @ManagedOperation(description = "Perform openshift metering for a single account.")
    @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
    public void performOpenshiftMeteringForAccount(String accountNumber) throws IllegalArgumentException {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Openshift metering for {} triggered via JMX by {}", accountNumber, principal);

        OffsetDateTime end = clock.now();
        OffsetDateTime start = getStartDate(end, servicePropeties.getRangeInMinutes());

        try {
            tasks.updateOpenshiftMetricsForAccount(accountNumber, start, end);
        }
        catch (Exception e) {
            log.error("Error triggering openshift metering for account {} via JMX.",
                accountNumber, e);
        }
    }

    @ManagedOperation(description = "Perform custom openshift metering for a single account.")
    @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
    @ManagedOperationParameter(
        name = "endDate",
        description = "The end date for metrics gathering (default: now). i.e 2018-03-20T09:12:28Z"
    )
    @ManagedOperationParameter(
        name = "rangeInMinutes",
        description = "Period of time (before the end date) to start metrics gathering. Must be >= 0."
    )
    public void performCustomOpenshiftMeteringForAccount(String accountNumber, String endDate,
        Integer rangeInMinutes) throws IllegalArgumentException {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Openshift metering for {} triggered via JMX by {}", accountNumber, principal);

        OffsetDateTime end = getDate(endDate);
        OffsetDateTime start = getStartDate(end, rangeInMinutes);

        try {
            tasks.updateOpenshiftMetricsForAccount(accountNumber, start, end);
        }
        catch (Exception e) {
            log.error("Error triggering openshift metering for account {} via JMX.",
                accountNumber, e);
        }
    }

    @ManagedOperation(description = "Perform openshift metering for all accounts.")
    public void performOpenshiftMetering()
        throws IllegalArgumentException {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Metering for all accounts triggered via JMX by {}", principal);

        OffsetDateTime end = clock.now();
        OffsetDateTime start = getStartDate(end, servicePropeties.getRangeInMinutes());

        try {
            tasks.updateOpenshiftMetricsForAllAccounts(start, end);
        }
        catch (Exception e) {
            log.error("Error triggering openshift metering for all accounts via JMX.", e);
        }
    }

    @ManagedOperation(description = "Perform custom openshift metering for all accounts.")
    @ManagedOperationParameter(
        name = "endDate",
        description = "The end date for metrics gathering. i.e 2018-03-20T09:12:28Z"
    )
    @ManagedOperationParameter(
        name = "rangeInMinutes",
        description = "Period of time (before the end date) to start metrics gathering. Must be >= 0."
    )
    public void performCustomOpenshiftMetering(String endDate, Integer rangeInMinutes)
        throws IllegalArgumentException {
        Object principal = ResourceUtils.getPrincipal();
        log.info("Metering for all accounts triggered via JMX by {}", principal);

        OffsetDateTime end = getDate(endDate);
        OffsetDateTime start = getStartDate(end, rangeInMinutes);

        try {
            tasks.updateOpenshiftMetricsForAllAccounts(start, end);
        }
        catch (Exception e) {
            log.error("Error triggering openshift metering for all accounts via JMX.", e);
        }
    }

    private OffsetDateTime getDate(String dateToParse) {
        try {
            // 2018-03-20T09:12:28Z
            return StringUtils.hasText(dateToParse) ? OffsetDateTime.parse(dateToParse) : null;
        }
        catch (Exception e) {
            throw new IllegalArgumentException(
                String.format("Unable to parse date arg '%s'. Invalid format.", dateToParse)
            );
        }
    }

    private OffsetDateTime getStartDate(OffsetDateTime endDate, Integer rangeInMinutes) {
        if (rangeInMinutes == null) {
            throw new IllegalArgumentException("Required argument: rangeInMinutes");
        }

        if (rangeInMinutes < 0) {
            throw new IllegalArgumentException(
                "Invalid value specified (Must be >= 0): rangeInMinutes");
        }

        return endDate.minusMinutes(rangeInMinutes);
    }

}
