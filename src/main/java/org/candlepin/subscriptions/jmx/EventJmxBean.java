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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
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

  private final ApplicationProperties applicationProperties;
  private final EventController eventController;
  private final ObjectMapper objectMapper;

  public EventJmxBean(
      ApplicationProperties applicationProperties,
      EventController eventController,
      ObjectMapper objectMapper) {
    this.applicationProperties = applicationProperties;
    this.eventController = eventController;
    this.objectMapper = objectMapper;
  }

  @Transactional
  @ManagedOperation(description = "Fetch events (for debugging).")
  @ManagedOperationParameter(name = "accountNumber", description = "Account number")
  @ManagedOperationParameter(name = "begin", description = "Beginning of time range (inclusive)")
  @ManagedOperationParameter(name = "end", description = "End of time range (exclusive)")
  public String fetchEventsInTimeRange(String accountNumber, String begin, String end) {
    OffsetDateTime beginValue = OffsetDateTime.parse(begin);
    OffsetDateTime endValue = OffsetDateTime.parse(end);
    Stream<Event> eventStream =
        eventController.fetchEventsInTimeRange(accountNumber, beginValue, endValue);
    return String.format("[%s]", eventStream.map(Event::toString).collect(Collectors.joining(",")));
  }

  @ManagedOperation(description = "Fetch an event")
  @ManagedOperationParameter(name = "eventId", description = "Event UUID")
  public String getEvent(String eventId) {
    return eventController.getEvent(UUID.fromString(eventId)).toString();
  }

  @ManagedOperation(description = "Save an event. Supported only in dev-mode.")
  @ManagedOperationParameter(name = "json", description = "Event JSON")
  public String saveEvent(String json) {
    if (!applicationProperties.isDevMode()) {
      throw new JmxException("Unsupported outside dev-mode!");
    }
    try {
      return eventController.saveEvent(objectMapper.readValue(json, Event.class)).toString();
    } catch (Exception e) {
      log.error("Error saving event", e);
      throw new JmxException("Error saving event. See log for details.");
    }
  }
}
