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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.tally.AccountResetService;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.springframework.jmx.JmxException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InternalTallyDataController {
  private final AccountResetService accountResetService;
  private final EventController eventController;
  private final CaptureSnapshotsTaskManager tasks;
  private final ObjectMapper objectMapper;

  public InternalTallyDataController(
      AccountResetService accountResetService,
      EventController eventController,
      CaptureSnapshotsTaskManager tasks,
      ObjectMapper objectMapper) {
    this.accountResetService = accountResetService;
    this.eventController = eventController;
    this.tasks = tasks;
    this.objectMapper = objectMapper;
  }

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

  public String saveEvents(String jsonListOfEvents) throws JmxException {
    List<Event> saved;
    try {
      saved =
          eventController.saveAll(
              objectMapper.readValue(
                  jsonListOfEvents,
                  objectMapper.getTypeFactory().constructCollectionType(List.class, Event.class)));
    } catch (Exception e) {
      log.error("Error saving events", e);
      return "Error saving events";
    }

    try {
      return objectMapper.writeValueAsString(saved);
    } catch (JsonProcessingException e) {
      log.error("Error serializing saved event data!", e);
      return "Error serializing saved event data";
    }
  }
}
