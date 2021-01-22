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
package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Exposes the ability to perform OptIn operations. */
@Component
@ManagedResource
public class OptInJmxBean {
  private static final Logger log = LoggerFactory.getLogger(OptInJmxBean.class);

  private final OptInController controller;
  private final ApplicationClock clock;
  private final AccountConfigRepository repo;

  public OptInJmxBean(
      OptInController controller, ApplicationClock clock, AccountConfigRepository repo) {
    this.controller = controller;
    this.clock = clock;
    this.repo = repo;
  }

  @ManagedOperation(description = "Fetch an opt in configuration")
  @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
  @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
  public String getOptInConfig(String accountNumber, String orgId) {
    return controller.getOptInConfig(accountNumber, orgId).toString();
  }

  @ManagedOperation(description = "Delete opt in configuration")
  @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
  @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
  public void optOut(String accountNumber, String orgId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Opt out for {} triggered via JMX by {}", accountNumber, principal);
    controller.optOut(accountNumber, orgId);
  }

  @ManagedOperation(
      description = "Create or update an opt in configuration. This operation is idempotent")
  @ManagedOperationParameter(name = "accountNumber", description = "Red Hat Account Number")
  @ManagedOperationParameter(name = "orgId", description = "Red Hat Org ID")
  @ManagedOperationParameter(name = "enableTallySync", description = "Turn on Tally syncing")
  @ManagedOperationParameter(name = "enableTallyReporting", description = "Turn on Tally reporting")
  @ManagedOperationParameter(name = "enableConduitSync", description = "Turn on Conduit syncing")
  public String createOrUpdateOptInConfig(
      String accountNumber,
      String orgId,
      boolean enableTallySync,
      boolean enableTallyReporting,
      boolean enableConduitSync) {
    Object principal = ResourceUtils.getPrincipal();
    log.info(
        "Opt in for account {}, org {} triggered via JMX by {}", accountNumber, orgId, principal);
    log.debug("Creating OptInConfig over JMX for account {}, org {}", accountNumber, orgId);
    OptInConfig config =
        controller.optIn(
            accountNumber,
            orgId,
            OptInType.JMX,
            enableTallySync,
            enableTallyReporting,
            enableConduitSync);

    String text = "Completed opt in for account %s and org %s:\n%s";
    return String.format(text, accountNumber, orgId, config.toString());
  }

  @ManagedAttribute(description = "Count of how many orgs opted-in in the previous week.")
  public int getLastWeekOptInCount() {
    return weekCount(OffsetDateTime.now().minusWeeks(1));
  }

  @ManagedAttribute(description = "Count of how many orgs opted-in in the current week.")
  public int getCurrentWeekOptInCount() {
    return weekCount(OffsetDateTime.now());
  }

  @ManagedOperation(description = "Fetch the number of orgs opted-in in a given week.")
  @ManagedOperationParameter(
      name = "weekOf",
      description = "Date in the week to query; YYYY-MM-DD format")
  public int getOptInCountForWeekOf(String weekOf) throws ParseException {
    OffsetDateTime dateInWeek =
        OffsetDateTime.ofInstant(
            new SimpleDateFormat("yyyy-MM-dd").parse(weekOf).toInstant(), ZoneOffset.UTC);
    return weekCount(dateInWeek);
  }

  protected int weekCount(OffsetDateTime weekDate) {
    OffsetDateTime begin = clock.startOfWeek(weekDate);
    OffsetDateTime end = clock.endOfWeek(weekDate);
    return repo.getCountOfOptInsForDateRange(begin, end);
  }
}
