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
package org.candlepin.subscriptions.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.TargetHost;
import org.candlepin.subscriptions.json.UsageEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventToUsageEventMapper {

  public UsageEvent mapToUsageEvent(EventRecord eventRecord) {
    log.debug("Mapping EventRecord to UsageEvent. eventId={}", eventRecord.getEventId());

    // Extract Event from EventRecord
    Event event = eventRecord.getEvent();

    // Check for empty measurements → return null, log warning
    if (event.getMeasurements() == null || event.getMeasurements().isEmpty()) {
      log.warn(
          "Event not mapped to UsageEvent! The Event has empty measurements. eventId={}",
          event.getEventId());
      return null;
    }

    // Check if measurements have product_tags (new format)
    boolean measurementsHaveProductTags =
        event.getMeasurements().stream()
            .anyMatch(m -> m.getProductTag() != null && !m.getProductTag().isBlank());

    // If using old format (top-level product_tag), validate it
    boolean hasTopLevelProductTag =
        event.getProductTag() != null && !event.getProductTag().isEmpty();

    if (!measurementsHaveProductTags && !hasTopLevelProductTag) {
      log.warn(
          "Event not mapped to UsageEvent! No product_tag found (neither in measurements nor at top level). eventId={}",
          event.getEventId());
      return null;
    }

    // Map simple fields (orgId, serviceType, timestamp, eventRecordId)
    UsageEvent usageEvent = new UsageEvent();
    usageEvent.setOrgId(event.getOrgId());
    usageEvent.setServiceType(event.getServiceType());
    usageEvent.setTimestamp(event.getTimestamp());

    // Use EventRecord ID if available (saved to DB), otherwise use Event's event_id
    String eventRecordId =
        eventRecord.getEventId() != null
            ? eventRecord.getEventId().toString()
            : String.valueOf(event.getEventId());
    usageEvent.setEventRecordId(eventRecordId);

    // Build targetHost object
    TargetHost targetHost = new TargetHost();
    targetHost.setInstanceId(event.getInstanceId());

    // Get displayName (might be null instead of Optional.empty() - handle both cases)
    Optional<String> displayNameOpt = event.getDisplayName();
    String displayName = (displayNameOpt != null) ? displayNameOpt.orElse(null) : null;
    targetHost.setDisplayName(displayName);

    usageEvent.setTargetHost(targetHost);

    // Build measurements array
    List<org.candlepin.subscriptions.json.Measurement> mappedMeasurements = new ArrayList<>();

    if (measurementsHaveProductTags) {
      // New format: product_tag is inside each measurement, use directly
      for (org.candlepin.subscriptions.json.Measurement measurement : event.getMeasurements()) {
        if (measurement.getProductTag() != null && !measurement.getProductTag().isBlank()) {
          org.candlepin.subscriptions.json.Measurement mappedMeasurement =
              new org.candlepin.subscriptions.json.Measurement();
          mappedMeasurement.setProductTag(measurement.getProductTag());
          mappedMeasurement.setMetricId(measurement.getMetricId());

          // Handle null values (default to 0.0 for null values)
          Double value =
              measurement.getValue() == null ? 0.0 : measurement.getValue().doubleValue();
          mappedMeasurement.setValue(value);
          mappedMeasurements.add(mappedMeasurement);
        } else {
          log.warn("Could not find product tag for the event with an id of {}", event.getEventId());
        }
      }
    } else {
      // Old format: Cartesian product of top-level product_tag × measurements
      for (String productTag : event.getProductTag()) {
        for (org.candlepin.subscriptions.json.Measurement measurement : event.getMeasurements()) {
          org.candlepin.subscriptions.json.Measurement mappedMeasurement =
              new org.candlepin.subscriptions.json.Measurement();
          mappedMeasurement.setProductTag(productTag);
          mappedMeasurement.setMetricId(measurement.getMetricId());

          // Handle null values (default to 0.0 for null values)
          Double value =
              measurement.getValue() == null ? 0.0 : measurement.getValue().doubleValue();
          mappedMeasurement.setValue(value);
          mappedMeasurements.add(mappedMeasurement);
        }
      }
    }

    usageEvent.setMeasurements(mappedMeasurements);

    log.debug(
        "Successfully mapped Event to UsageEvent. eventId={}, measurements={}",
        event.getEventId(),
        mappedMeasurements.size());
    return usageEvent;
  }
}
