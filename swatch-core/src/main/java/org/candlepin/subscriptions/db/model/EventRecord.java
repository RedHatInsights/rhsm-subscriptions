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
package org.candlepin.subscriptions.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.candlepin.subscriptions.json.Event;

/**
 * DB entity for an event record.
 *
 * <p>An event record consists of an ID and a JSON document with the event data.
 */
@Entity
@Table(name = "events")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@IdClass(EventRecordId.class)
public class EventRecord {

  /**
   * Create a new EventRecord from a given Event.
   *
   * <p>Generates a new random UUID if the Event does not have a UUID.
   *
   * @param event the event object
   */
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public EventRecord(Event event) {
    Objects.requireNonNull(event, "event must not be null");
    this.meteringBatchId = event.getMeteringBatchId();
    this.event = event;
    this.orgId = event.getOrgId();
    this.eventType = event.getEventType();
    this.eventSource = event.getEventSource();
    this.instanceId = event.getInstanceId();
    this.timestamp = event.getTimestamp();
  }

  @Column(name = "event_id", updatable = false)
  private UUID eventId;

  @Id
  @Column(name = "org_id")
  private String orgId;

  @Id
  @Column(name = "event_type")
  private String eventType;

  @Id
  @Column(name = "event_source")
  private String eventSource;

  @Id
  @Column(name = "instance_id")
  private String instanceId;

  @Column(name = "metering_batch_id")
  private UUID meteringBatchId;

  @Id private OffsetDateTime timestamp;

  /*
  Since we have a bitemporal pattern, the "timestamp" and "actual_date" means the same.
  Accordingly, "record_date" refers to the date when we entered the activity into our system.

  For reference: https://martinfowler.com/articles/bitemporal-history.html#TheTwoDimensions
  */

  @Id
  @Column(name = "record_date", updatable = false)
  private OffsetDateTime recordDate;

  @Valid
  @Column(name = "data")
  @Convert(converter = EventRecordConverter.class)
  private Event event;

  @PrePersist
  public void populateEventId() {
    this.recordDate = OffsetDateTime.now(ZoneId.of("UTC"));

    if (event == null) {
      return;
    }

    if (event.getEventId() == null) {
      event.setEventId(UUID.randomUUID());
    }

    this.eventId = event.getEventId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EventRecord that = (EventRecord) o;
    return Objects.equals(orgId, that.getOrgId())
        && Objects.equals(eventType, that.eventType)
        && Objects.equals(eventSource, that.eventSource)
        && Objects.equals(instanceId, that.instanceId)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(recordDate, that.recordDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orgId, eventType, eventSource, instanceId, timestamp, recordDate);
  }

  /**
   * Provides a convenient way to fetch the embedded record ID as an Object.
   *
   * @return the EventRecordId for this event object.
   */
  @Transient
  public EventRecordId getEventRecordId() {
    return new EventRecordId(
        this.orgId,
        this.eventType,
        this.eventSource,
        this.instanceId,
        this.timestamp,
        this.recordDate);
  }
}
