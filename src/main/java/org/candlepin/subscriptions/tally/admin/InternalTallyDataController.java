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
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.event.EventNormalizer;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.tally.AccountResetService;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.utilization.api.v1.model.OptInConfig;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class InternalTallyDataController {
  private final AccountResetService accountResetService;
  private final EventController eventController;
  private final CaptureSnapshotsTaskManager tasks;
  private final ObjectMapper objectMapper;
  private final OptInController controller;
  private final TallySnapshotController snapshotController;
  private final EventNormalizer eventNormalizer;
  private final DataMigrationRunner dataMigrationRunner;
  private final MergeHostsMigration mergeHostsMigration;

  public void deleteDataAssociatedWithOrg(String orgId) {
    accountResetService.deleteDataForOrg(orgId);
  }

  public void deleteEvent(String eventId) {
    eventController.deleteEvent(UUID.fromString(eventId));
  }

  public void tallyConfiguredOrgs() {
    tasks.updateSnapshotsForAllOrg();
  }

  public void tallyOrg(String orgId) {
    tasks.updateOrgSnapshots(orgId);
  }

  public void tallyOrgSync(String orgId) {
    snapshotController.produceSnapshotsForOrg(orgId);
  }

  public String saveEvents(String jsonListOfEvents) {
    List<EventRecord> saved;
    List<Event> events;

    try {
      events =
          objectMapper.readValue(jsonListOfEvents, new TypeReference<List<Event>>() {}).stream()
              .map(eventNormalizer::normalizeEvent)
              .filter(
                  e -> {
                    if (eventController.validateServiceInstanceEvent(e)) {
                      return true;
                    } else {
                      log.warn("Invalid event in batch: {}", e);
                      return false;
                    }
                  })
              .toList();
    } catch (Exception e) {
      log.warn("Error parsing request body");
      throw new BadRequestException(e.getMessage());
    }

    try {
      saved = eventController.saveAllEventRecords(eventController.resolveEventConflicts(events));
    } catch (Exception e) {
      log.error("Error saving events, {}", e.getMessage());
      return "Error saving events";
    }

    try {
      log.info("Events saved: {}", objectMapper.writeValueAsString(saved));
      return "Events saved";
    } catch (JsonProcessingException e) {
      log.error("Error serializing saved event data!", e);
      return "Error serializing saved event data";
    }
  }

  @Transactional
  public String fetchEventsForOrgIdInTimeRange(
      String orgId, OffsetDateTime begin, OffsetDateTime end) throws JsonProcessingException {
    List<Event> events = eventController.fetchEventsInTimeRange(orgId, begin, end).toList();
    return objectMapper.writeValueAsString(events);
  }

  public void tallyAllOrgsByHourly() throws IllegalArgumentException {
    tasks.updateHourlySnapshotsForAllOrgs();
  }

  public void mergeHostsFromMultipleSources(String orgId) {
    mergeHostsMigration.setOrgId(orgId);
    dataMigrationRunner.migrate(mergeHostsMigration, null, 10);
  }

  public String createOrUpdateOptInConfig(String orgId, OptInType api) {
    OptInConfig config = controller.optIn(orgId, api);

    log.info("Completed opt in for org {}: \n{}", orgId, config.toString());
    String text = "Completed opt in for org %s";
    return String.format(text, orgId);
  }
}
