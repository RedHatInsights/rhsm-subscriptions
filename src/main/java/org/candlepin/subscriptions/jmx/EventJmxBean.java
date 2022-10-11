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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * JMX Bean for interacting with the event store.
 *
 * <p>Allows insertion of event JSON *only in dev-mode*.
 */
@Component
@ManagedResource
public class EventJmxBean {

  private static final Logger log = LoggerFactory.getLogger(EventJmxBean.class);
  public static final String FEATURE_NOT_ENABLED_MESSSAGE =
      "This feature is not currently enabled.";

  private final EventController eventController;
  private final ObjectMapper objectMapper;
  private final SecurityProperties properties;

  private final AccountConfigRepository accountConfigRepository;

  public EventJmxBean(
      EventController eventController,
      ObjectMapper objectMapper,
      SecurityProperties properties,
      AccountConfigRepository accountConfigRepository) {
    this.eventController = eventController;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.accountConfigRepository = accountConfigRepository;
  }

  @Transactional
  @ManagedOperation(description = "Fetch events (for debugging).")
  @ManagedOperationParameter(name = "accountNumber", description = "Account number")
  @ManagedOperationParameter(name = "begin", description = "Beginning of time range (inclusive)")
  @ManagedOperationParameter(name = "end", description = "End of time range (exclusive)")
  public String fetchEventsInTimeRange(String accountNumber, String begin, String end) {
    String orgId = accountConfigRepository.findOrgByAccountNumber(accountNumber);
    if (orgId == null) {
      throw new JmxException("Unable to find orgId for accountNumber: " + accountNumber);
    }
    return fetchEventsForOrgIdInTimeRange(orgId, begin, end);
  }

  @Transactional
  @ManagedOperation(description = "Fetch events (for debugging).")
  @ManagedOperationParameter(name = "accountNumber", description = "Account number")
  @ManagedOperationParameter(name = "begin", description = "Beginning of time range (inclusive)")
  @ManagedOperationParameter(name = "end", description = "End of time range (exclusive)")
  public String fetchEventsForOrgIdInTimeRange(String orgId, String begin, String end) {
    OffsetDateTime beginValue = OffsetDateTime.parse(begin);
    OffsetDateTime endValue = OffsetDateTime.parse(end);

    try {
      List<Event> events =
          eventController
              .fetchEventsInTimeRange(orgId, beginValue, endValue)
              .collect(Collectors.toList());
      return objectMapper.writeValueAsString(events);
    } catch (Exception e) {
      throw new JmxException("Unable to deserialize event list.", e);
    }
  }

  @ManagedOperation(description = "Fetch an event")
  @ManagedOperationParameter(name = "eventId", description = "Event UUID")
  public String getEvent(String eventId) {
    Optional<Event> event = eventController.getEvent(UUID.fromString(eventId));
    try {
      return event.isPresent() ? objectMapper.writeValueAsString(event.get()) : null;
    } catch (Exception e) {
      throw new JmxException("Unable to serialize event!", e);
    }
  }

  @ManagedOperation(description = "Delete an event. Supported only in dev-mode.")
  @ManagedOperationParameter(name = "eventId", description = "Event UUID")
  public String deleteEvent(String eventId) {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      throw new JmxException(FEATURE_NOT_ENABLED_MESSSAGE);
    }
    try {
      eventController.deleteEvent(UUID.fromString(eventId));
      return String.format("Successfully deleted Event with ID: %s", eventId);
    } catch (Exception e) {
      return String.format(
          "Failed to delete Event with ID: %s  Cause: %s", eventId, e.getMessage());
    }
  }

  @ManagedOperation(description = "Save an event. Supported only in dev-mode.")
  @ManagedOperationParameter(name = "json", description = "Event JSON")
  public String saveEvent(String json) throws JmxException {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      throw new JmxException(FEATURE_NOT_ENABLED_MESSSAGE);
    }
    Event event;
    try {
      event = eventController.saveEvent(objectMapper.readValue(json, Event.class));
    } catch (JsonProcessingException e) {
      throw new JmxException("Error deserializing incoming event data", e);
    } catch (Exception e) {
      throw new JmxException("Error saving event. See log for details.", e);
    }

    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new JmxException("Error serializing saved event data!", e);
    }
  }

  @ManagedOperation(description = "Save a list of events. Supported only in dev-mode.")
  @ManagedOperationParameter(
      name = "jsonListOfEvents",
      description = "Event list specified as JSON")
  public String saveEvents(String jsonListOfEvents) throws JmxException {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      throw new JmxException(FEATURE_NOT_ENABLED_MESSSAGE);
    }
    List<Event> saved;
    try {
      saved =
          eventController.saveAll(
              objectMapper.readValue(
                  jsonListOfEvents,
                  objectMapper.getTypeFactory().constructCollectionType(List.class, Event.class)));
    } catch (Exception e) {
      log.error("Error saving events", e);
      throw new JmxException("Error saving event. See log for details.");
    }

    try {
      return objectMapper.writeValueAsString(saved);
    } catch (JsonProcessingException e) {
      throw new JmxException("Error serializing saved event data!", e);
    }
  }
}
