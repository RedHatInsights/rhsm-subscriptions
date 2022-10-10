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
package org.candlepin.subscriptions.jmx;

import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.util.DateRange;
import org.candlepin.subscriptions.validator.ParameterDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/** Exposes the ability to trigger a tally for an account from JMX. */
@Component
@Validated
@ManagedResource
public class TallyJmxBean {

  private static final Logger log = LoggerFactory.getLogger(TallyJmxBean.class);

  private final CaptureSnapshotsTaskManager tasks;

  public TallyJmxBean(CaptureSnapshotsTaskManager taskManager) {
    this.tasks = taskManager;
  }

  // Deprecate this endpoint once accountNumber to orgId is finished.
  @ManagedOperation(description = "Trigger a tally for an account")
  @ManagedOperationParameter(name = "accountNumber", description = "Which account to tally.")
  public void tallyAccount(String accountNumber) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for account {} triggered over JMX by {}", accountNumber, principal);
    tasks.updateAccountSnapshots(accountNumber);
  }

  @ManagedOperation(description = "Trigger a tally for an org")
  @ManagedOperationParameter(name = "orgId", description = "Which account to tally.")
  public void tallyOrg(String orgId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for org {} triggered over JMX by {}", orgId, principal);
    tasks.updateOrgSnapshots(orgId);
  }

  // Later during clean up rename the endpoint to tallyConfiguredOrgs
  @ManagedOperation(description = "Trigger tally for all configured accounts")
  public void tallyConfiguredAccounts() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for all orgs triggered over JMX by {}", principal);
    tasks.updateSnapshotsForAllOrg();
  }

  @ManagedOperation(description = "Trigger hourly tally for an account within a timeframe.")
  @ManagedOperationParameter(name = "accountNumber", description = "Which account to tally.")
  @ManagedOperationParameter(
      name = "startDateTime",
      description =
          "Beginning of the range of time the tally should include. "
              + "Should be top of the hour and expected to be in ISO 8601 format (e.g. 2020-08-02T14:00Z).")
  @ManagedOperationParameter(
      name = "endDateTime",
      description =
          "Ending of the range of time the tally should include. "
              + "Should be top of the hour and expected to be in ISO 8601 format (e.g. 2020-08-02T14:00Z).")
  @ParameterDuration("@jmxProperties.tallyBean.hourlyTallyDurationLimitDays")
  public void tallyAccountByHourly(
      String accountNumber, @NotNull String startDateTime, @NotNull String endDateTime) {
    log.info(
        "Hourly tally between {} and {} for account {} triggered over JMX by {}",
        startDateTime,
        endDateTime,
        accountNumber,
        ResourceUtils.getPrincipal());
    var tallyRange = DateRange.fromStrings(startDateTime, endDateTime);
    tasks.tallyAccountByHourly(accountNumber, tallyRange);
  }

  @ManagedOperation(
      description =
          "Trigger hourly tally for all configured accounts for the specified range. The 'start' and "
              + "'end' parameters MUST be specified as a pair to complete a range. If they are left empty, "
              + "a date range is used based on NOW with the configured offsets applied (identical to if "
              + " the job was run).")
  @ManagedOperationParameter(
      name = "start",
      description =
          "The start date for the tally (e.g. 22-05-03T10:00:00Z). Must be specified along with the end parameter.")
  @ManagedOperationParameter(
      name = "end",
      description =
          "The end date for the tally (e.g. 22-05-03T16:00:00Z). Must be specified along with the start parameter.")
  public void tallyAllAccountsByHourly(String start, String end) throws IllegalArgumentException {

    DateRange range = null;
    if (StringUtils.hasText(start) || StringUtils.hasText(end)) {
      try {
        range = DateRange.fromStrings(start, end);
        log.info(
            "Hourly tally for all accounts triggered for range {} over JMX by {}",
            range,
            ResourceUtils.getPrincipal());
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Both startDateTime and endDateTime must be set to " + "valid date Strings.");
      }
    } else {
      log.info(
          "Hourly tally for all accounts triggered over JMX by {}", ResourceUtils.getPrincipal());
    }

    tasks.updateHourlySnapshotsForAllAccounts(Optional.ofNullable(range));
  }
}
