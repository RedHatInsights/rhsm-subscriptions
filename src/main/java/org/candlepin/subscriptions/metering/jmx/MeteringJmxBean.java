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
package org.candlepin.subscriptions.metering.jmx;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
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

/** Exposes the ability to trigger metering operations from JMX. */
@ManagedResource
public class MeteringJmxBean {

  private static final Logger log = LoggerFactory.getLogger(MeteringJmxBean.class);

  private PrometheusMetricsTaskManager tasks;

  private ApplicationClock clock;

  private MetricProperties metricProperties;

  @Autowired
  public MeteringJmxBean(
      ApplicationClock clock,
      PrometheusMetricsTaskManager tasks,
      MetricProperties metricProperties) {
    this.clock = clock;
    this.tasks = tasks;
    this.metricProperties = metricProperties;
  }

  @ManagedOperation(description = "Perform product metering for a single account.")
  @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
  @ManagedOperationParameter(name = "productTag", description = "Product tag identifier")
  public void performMeteringForAccount(String accountNumber, String productTag)
      throws IllegalArgumentException {
    Object principal = ResourceUtils.getPrincipal();
    log.info("{} metering for {} triggered via JMX by {}", productTag, accountNumber, principal);

    OffsetDateTime end = getDate(null);
    OffsetDateTime start = getStartDate(end, metricProperties.getRangeInMinutes());

    try {
      tasks.updateMetricsForAccount(accountNumber, productTag, start, end);
    } catch (Exception e) {
      log.error(
          "Error triggering {} metering for account {} via JMX.", productTag, accountNumber, e);
    }
  }

  @ManagedOperation(description = "Perform custom product metering for a single account.")
  @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
  @ManagedOperationParameter(name = "productTag", description = "Product tag identifier")
  @ManagedOperationParameter(
      name = "endDate",
      description =
          "The end date for metrics gathering. Must start at top of the hour. i.e 2018-03-20T09:00:00Z")
  @ManagedOperationParameter(
      name = "rangeInMinutes",
      description =
          "Period of time (before the end date) to start metrics gathering. Must be >= 0.")
  public void performCustomMeteringForAccount(
      String accountNumber, String productTag, String endDate, Integer rangeInMinutes)
      throws IllegalArgumentException {
    Object principal = ResourceUtils.getPrincipal();
    log.info("{} metering for {} triggered via JMX by {}", productTag, accountNumber, principal);

    OffsetDateTime end = getDate(endDate);
    OffsetDateTime start = getStartDate(end, rangeInMinutes);

    try {
      tasks.updateMetricsForAccount(accountNumber, productTag, start, end);
    } catch (Exception e) {
      log.error(
          "Error triggering {} metering for account {} via JMX.", productTag, accountNumber, e);
    }
  }

  @ManagedOperation(description = "Perform a product metering for all accounts.")
  public void performMetering(String productTag) throws IllegalArgumentException {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Metering for all accounts triggered via JMX by {}", principal);

    OffsetDateTime end = getDate(null);
    OffsetDateTime start = getStartDate(end, metricProperties.getRangeInMinutes());

    try {
      tasks.updateMetricsForAllAccounts(productTag, start, end);
    } catch (Exception e) {
      log.error("Error triggering {} metering for all accounts via JMX.", productTag, e);
    }
  }

  @ManagedOperation(description = "Perform custom product metering for all accounts.")
  @ManagedOperationParameter(name = "productTag", description = "Product tag identifier")
  @ManagedOperationParameter(
      name = "endDate",
      description =
          "The end date for metrics gathering. Must start at top of the hour. i.e 2018-03-20T09:00:00Z")
  @ManagedOperationParameter(
      name = "rangeInMinutes",
      description =
          "Period of time (before the end date) to start metrics gathering. Must be >= 0.")
  public void performCustomMetering(String productTag, String endDate, Integer rangeInMinutes)
      throws IllegalArgumentException {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Metering for all accounts triggered via JMX by {}", principal);

    OffsetDateTime end = getDate(endDate);
    OffsetDateTime start = getStartDate(end, rangeInMinutes);

    try {
      tasks.updateMetricsForAllAccounts(productTag, start, end);
    } catch (Exception e) {
      log.error("Error triggering {} metering for all accounts via JMX.", productTag, e);
    }
  }

  private OffsetDateTime getDate(String dateToParse) {
    if (StringUtils.hasText(dateToParse)) {
      try {
        // 2018-03-20T09:00:00Z
        OffsetDateTime parsed = OffsetDateTime.parse(dateToParse);
        if (!parsed.isEqual(clock.startOfHour(parsed))) {
          throw new IllegalArgumentException(
              String.format("Date must start at top of the hour: %s", parsed));
        }
        return parsed;
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            String.format("Unable to parse date arg '%s'. Invalid format.", dateToParse));
      }
    } else {
      return clock.startOfCurrentHour();
    }
  }

  private OffsetDateTime getStartDate(OffsetDateTime endDate, Integer rangeInMinutes) {
    if (rangeInMinutes == null) {
      throw new IllegalArgumentException("Required argument: rangeInMinutes");
    }

    if (rangeInMinutes < 0) {
      throw new IllegalArgumentException("Invalid value specified (Must be >= 0): rangeInMinutes");
    }

    return endDate.minusMinutes(rangeInMinutes);
  }
}
