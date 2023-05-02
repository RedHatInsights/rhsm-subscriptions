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
package org.candlepin.subscriptions.tally.admin;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.AccountResetService;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.admin.api.InternalApi;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResend;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResendData;
import org.candlepin.subscriptions.tally.admin.api.model.UuidList;
import org.candlepin.subscriptions.tally.billing.RemittanceController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/** This resource is for exposing administrator REST endpoints for Tally. */
@Component
@Slf4j
public class InternalTallyResource implements InternalApi {

  private final ApplicationClock clock;
  private final ApplicationProperties applicationProperties;
  private final MarketplaceResendTallyController resendTallyController;
  private final RemittanceController remittanceController;
  private final TallySnapshotController tallySnapshotController;
  private final CaptureSnapshotsTaskManager snapshotsTaskManager;
  private final TallyRetentionController retentionController;
  private final AccountResetService accountResetService;
  private final EventController eventController;
  private final SecurityProperties properties;
  public static final String FEATURE_NOT_ENABLED_MESSSAGE = "Account Reset feature not enabled.";

  @SuppressWarnings("java:S107")
  public InternalTallyResource(
      ApplicationClock clock,
      ApplicationProperties applicationProperties,
      MarketplaceResendTallyController resendTallyController,
      RemittanceController remittanceController,
      TallySnapshotController tallySnapshotController,
      CaptureSnapshotsTaskManager snapshotsTaskManager,
      TallyRetentionController retentionController,
      AccountResetService accountResetService,
      EventController eventController,
      SecurityProperties properties) {
    this.clock = clock;
    this.applicationProperties = applicationProperties;
    this.resendTallyController = resendTallyController;
    this.remittanceController = remittanceController;
    this.tallySnapshotController = tallySnapshotController;
    this.snapshotsTaskManager = snapshotsTaskManager;
    this.retentionController = retentionController;
    this.accountResetService = accountResetService;
    this.eventController = eventController;
    this.properties = properties;
  }

  @Override
  public void performHourlyTallyForOrg(
      String orgId, OffsetDateTime start, OffsetDateTime end, Boolean xRhSwatchSynchronousRequest) {
    DateRange range = new DateRange(start, end);
    if (!clock.isHourlyRange(range)) {
      throw new IllegalArgumentException(
          String.format(
              "Start/End times must be at the top of the hour: [%s -> %s]",
              range.getStartString(), range.getEndString()));
    }

    if (ResourceUtils.sanitizeBoolean(xRhSwatchSynchronousRequest, false)) {
      if (!applicationProperties.isEnableSynchronousOperations()) {
        throw new BadRequestException("Synchronous tally operations are not enabled.");
      }
      log.info("Synchronous hourly tally requested for orgId {}: {}", orgId, range);
      tallySnapshotController.produceHourlySnapshotsForOrg(orgId, range);
    } else {
      snapshotsTaskManager.tallyOrgByHourly(orgId, range);
    }
  }

  @Override
  public TallyResend resendTally(UuidList uuidList) {
    var tallies = resendTallyController.resendTallySnapshots(uuidList.getUuids());
    return new TallyResend().data(new TallyResendData().talliesResent(tallies));
  }

  @Override
  public void syncRemittance() {
    remittanceController.syncRemittance();
  }

  @Override
  public void purgeTallySnapshots() {
    try {
      log.info("Initiating tally snapshot purge.");
      retentionController.purgeSnapshotsAsync();
    } catch (TaskRejectedException e) {
      log.warn("A tally snapshots purge job is already running.");
    }
  }

  /**
   * "Clear tallies, hosts, and events for a given org ID. Enabled via ENABLE_ACCOUNT_RESET
   * environment variable. Intended only for non-prod environments.
   *
   * @param orgId Red Hat orgId
   */
  @Override
  public String deleteDataAssociatedWithOrg(String orgId) {
    if (!properties.isResetAccountEnabled() && !properties.isDevMode()) {
      log.error(FEATURE_NOT_ENABLED_MESSSAGE);
    }

    log.info("Received request to delete all data associated with orgId {}", orgId);

    try {
      accountResetService.deleteDataForOrg(orgId);
    } catch (Exception e) {
      log.error("Unable to delete data for organization {} due to {}", orgId, e);
    }

    var successMessage = "Finished deleting data associated with organization " + orgId;

    log.info(successMessage);

    return successMessage;
  }

  /**
   * Delete an event. Supported only in dev-mode.
   *
   * @param eventId Event Id
   * @return success or error message
   */
  @Override
  public String deleteEvent(String eventId) {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      log.error(FEATURE_NOT_ENABLED_MESSSAGE);
    }
    try {
      eventController.deleteEvent(UUID.fromString(eventId));
      return String.format("Successfully deleted Event with ID: %s", eventId);
    } catch (Exception e) {
      return String.format(
          "Failed to delete Event with ID: %s  Cause: %s", eventId, e.getMessage());
    }
  }
}
