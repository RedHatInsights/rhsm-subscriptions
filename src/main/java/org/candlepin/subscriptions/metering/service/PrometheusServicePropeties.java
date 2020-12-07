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
package org.candlepin.subscriptions.metering.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Properties related to the PrometheusService.
 */

@Component
@ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.service")
public class PrometheusServicePropeties {
    private String openshiftMetricsPromQL =
        "group(subscription_labels{ebs_account='%s'})by(_id,ebs_account,support)*on(_id)" +
        "group_right(ebs_account,support)cluster:usage:workload:capacity_physical_cpu_cores:min:5m";

    private int requestTimeout = 10000;

    /**
     * Defines the amount of time (in minutes) that will be used to calculate a metric query's
     * start date based on a given end date.
     *
     * For example, given an end date of 2021-01-06T00:00:00Z and a rangeInMinutes of 60, the
     * calculated start date will be: 2021-01-05T23:00:00Z.
     */
    private int rangeInMinutes = 60;

    /**
     * The amount of time for each openshift metric data point for the time range specified in the query.
     * This value should be specified in seconds.
     */
    private int openshiftMetricStep = 3600; // 1 hour

    public String getOpenshiftMetricsPromQL() {
        return openshiftMetricsPromQL;
    }

    public void setOpenshiftMetricsPromQL(String openshiftMetricsPromQL) {
        this.openshiftMetricsPromQL = openshiftMetricsPromQL;
    }

    public int getOpenshiftMetricStep() {
        return openshiftMetricStep;
    }

    public void setOpenshiftMetricStep(int openshiftMetricStep) {
        this.openshiftMetricStep = openshiftMetricStep;
    }

    public int getRangeInMinutes() {
        return rangeInMinutes;
    }

    public void setRangeInMinutes(int rangeInMinutes) {
        this.rangeInMinutes = rangeInMinutes;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
