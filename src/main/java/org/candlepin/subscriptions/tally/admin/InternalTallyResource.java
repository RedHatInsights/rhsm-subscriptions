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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.admin.api.InternalTallyApi;
import org.candlepin.subscriptions.tally.admin.api.model.DefaultResponse;
import org.candlepin.subscriptions.tally.admin.api.model.EventsResponse;
import org.candlepin.subscriptions.tally.admin.api.model.OptInResponse;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResend;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResendData;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResponse;
import org.candlepin.subscriptions.tally.admin.api.model.UuidList;
import org.candlepin.subscriptions.tally.events.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** This resource is for exposing administrator REST endpoints for Tally. */
@Component
@Slf4j
public class InternalTallyResource implements InternalTallyApi {

  public static final String FEATURE_NOT_ENABLED_MESSSAGE =
      "This feature is not currently enabled.";
  private static final String SUCCESS_STATUS = "Success";
  private static final String REJECTED_STATUS = "Rejected";

  private final ApplicationClock clock;
  private final ApplicationProperties applicationProperties;
  private final MarketplaceResendTallyController resendTallyController;
  private final CaptureSnapshotsTaskManager snapshotsTaskManager;
  private final TallyRetentionController tallyRetentionController;
  private final InternalTallyDataController internalTallyDataController;
  private final SecurityProperties properties;
  private final EventRecordRepository eventRecordRepository;
  private final EventRecordsRetentionProperties eventRecordsRetentionProperties;
  private final KafkaTemplate<String, Event> eventKafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String eventTopic;

  @SuppressWarnings("java:S107")
  public InternalTallyResource(
      ApplicationClock clock,
      ApplicationProperties applicationProperties,
      MarketplaceResendTallyController resendTallyController,
      CaptureSnapshotsTaskManager snapshotsTaskManager,
      TallyRetentionController tallyRetentionController,
      InternalTallyDataController internalTallyDataController,
      SecurityProperties properties,
      EventRecordRepository eventRecordRepository,
      EventRecordsRetentionProperties eventRecordsRetentionProperties,
      ObjectMapper objectMapper,
      KafkaTemplate<String, Event> eventKafkaTemplate,
      @Qualifier("serviceInstanceTopicProperties")
          TaskQueueProperties serviceInstanceTopicProperties) {
    this.clock = clock;
    this.applicationProperties = applicationProperties;
    this.resendTallyController = resendTallyController;
    this.snapshotsTaskManager = snapshotsTaskManager;
    this.tallyRetentionController = tallyRetentionController;
    this.internalTallyDataController = internalTallyDataController;
    this.properties = properties;
    this.eventRecordRepository = eventRecordRepository;
    this.eventRecordsRetentionProperties = eventRecordsRetentionProperties;
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
    this.eventTopic = serviceInstanceTopicProperties.getTopic();
  }

  @Override
  public void performHourlyTallyForOrg(String orgId, Boolean xRhSwatchUseThreadPoolExecutor) {
    snapshotsTaskManager.tallyOrgByHourly(
        orgId, ResourceUtils.sanitizeBoolean(xRhSwatchUseThreadPoolExecutor, false));
  }

  @Override
  public TallyResend resendTally(UuidList uuidList) {
    var tallies = resendTallyController.resendTallySnapshots(uuidList.getUuids());
    return new TallyResend().data(new TallyResendData().talliesResent(tallies));
  }

  @Override
  public DefaultResponse purgeTallySnapshots() {
    try {
      log.info("Initiating tally snapshot purge.");
      tallyRetentionController.purgeSnapshotsAsync();
    } catch (TaskRejectedException e) {
      log.warn("A tally snapshots purge job is already running.");
      return getDefaultResponse(REJECTED_STATUS);
    }
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /**
   * Clear tallies, hosts, and events for a given org ID. Enabled via ENABLE_ACCOUNT_RESET
   * environment variable. Intended only for non-prod environments.
   *
   * @param orgId Red Hat orgId
   */
  @Override
  public TallyResponse deleteDataAssociatedWithOrg(String orgId) {
    var response = new TallyResponse();
    if (isFeatureEnabled()) {
      log.info("Received request to delete all data associated with orgId {}", orgId);
      try {
        internalTallyDataController.deleteDataAssociatedWithOrg(orgId);
      } catch (Exception e) {
        log.error("Unable to delete data for organization {}", orgId, e);
        throw e;
      }
      var successMessage = "Finished deleting data associated with organization " + orgId;
      response.setDetail(successMessage);
      log.info(successMessage);
      return response;
    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  @Override
  @Transactional
  @Timed("rhsm-subscriptions.events.purge")
  public void purgeEventRecords() {
    var eventRetentionDuration = eventRecordsRetentionProperties.getEventRetentionDuration();

    OffsetDateTime cutoffDate =
        clock.now().truncatedTo(ChronoUnit.DAYS).minus(eventRetentionDuration);

    log.info("Purging event records older than {}", cutoffDate);
    eventRecordRepository.deleteInBulkEventRecordsByTimestampBefore(cutoffDate);
    log.info("Event record purge completed successfully");
  }

  /**
   * Delete an event. Supported only in dev-mode.
   *
   * @param eventId Event Id
   * @return success or error message
   */
  @Override
  public EventsResponse deleteEvent(String eventId) {
    var response = new EventsResponse();
    if (isFeatureEnabled()) {
      try {
        internalTallyDataController.deleteEvent(eventId);
        response.setDetail(String.format("Successfully deleted Event with ID: %s", eventId));
        return response;
      } catch (Exception e) {
        log.error("Failed to delete Event with ID: {}  Cause: {}", eventId, e.getMessage());
        response.setDetail(String.format("Failed to delete Event with ID: %s ", eventId));
        return response;
      }
    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  /**
   * Fetch events by org
   *
   * @param orgId Red Hat orgId
   * @param begin Beginning of time range (inclusive)
   * @param end End of time range (exclusive)
   * @return success or error message
   */
  @Override
  public EventsResponse fetchEventsForOrgIdInTimeRange(
      String orgId, OffsetDateTime begin, OffsetDateTime end) {
    var response = new EventsResponse();
    try {
      response.setDetail(
          internalTallyDataController.fetchEventsForOrgIdInTimeRange(orgId, begin, end));
      return response;
    } catch (Exception e) {
      log.error("Unable to deserialize event list ", e);
      response.setDetail("Unable to deserialize event list");
      return response;
    }
  }

  /**
   * Save a list of events. Supported only in dev-mode.
   *
   * @param jsonListOfEvents Event list specified as JSON
   * @return success or error message
   */
  @Override
  public EventsResponse saveEvents(String jsonListOfEvents) {
    var response = new EventsResponse();
    StringBuilder messages = new StringBuilder();
    if (isFeatureEnabled()) {
      try {
        List<Event> events = objectMapper.readValue(jsonListOfEvents, new TypeReference<>() {});
        events.stream()
            .forEach(
                event -> {
                  try {
                    eventKafkaTemplate.send(this.eventTopic, event);
                  } catch (Exception e) {
                    messages.append(e.getMessage());
                  }
                });
      } catch (JsonProcessingException e) {
        messages.append(e.getMessage());
      }
      response.setDetail(String.valueOf(messages));
      return response;

    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  /** Update tally snapshots for all orgs */
  @Override
  public DefaultResponse tallyConfiguredOrgs() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for all orgs triggered over API by {}", principal);
    internalTallyDataController.tallyConfiguredOrgs();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /** Trigger a tally for an org */
  @Override
  public DefaultResponse tallyOrg(String orgId, Boolean xRhSwatchSynchronousRequest) {
    Object principal = ResourceUtils.getPrincipal();
    LogUtils.addOrgIdToMdc(orgId);
    log.info("Tally for org {} triggered over API by {}", orgId, principal);
    if (ResourceUtils.sanitizeBoolean(xRhSwatchSynchronousRequest, false)) {
      if (!applicationProperties.isEnableSynchronousOperations()) {
        throw new BadRequestException("Synchronous tally operations are not enabled.");
      }
      log.info("Synchronous tally requested for orgId {}", orgId);
      internalTallyDataController.tallyOrgSync(orgId);
    } else {
      internalTallyDataController.tallyOrg(orgId);
    }

    LogUtils.clearOrgIdFromMdc();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /** Trigger hourly tally for all configured orgs. */
  @Override
  public DefaultResponse tallyAllOrgsByHourly() {
    log.info(
        "Hourly tally for all accounts triggered over API by {}", ResourceUtils.getPrincipal());

    internalTallyDataController.tallyAllOrgsByHourly();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /**
   * Create or update an opt in configuration. This operation is idempotent
   *
   * @param orgId
   * @return success or error message
   */
  @Override
  public OptInResponse createOrUpdateOptInConfig(String orgId) {
    var response = new OptInResponse();
    Object principal = ResourceUtils.getPrincipal();
    log.info("Opt in for org {} triggered via API by {}", orgId, principal);
    log.debug("Creating OptInConfig over API for org {}", orgId);
    response.setDetail(internalTallyDataController.createOrUpdateOptInConfig(orgId, OptInType.API));
    return response;
  }

  private boolean isFeatureEnabled() {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      log.error(FEATURE_NOT_ENABLED_MESSSAGE);
      return false;
    }
    return true;
  }

  @NotNull
  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}
