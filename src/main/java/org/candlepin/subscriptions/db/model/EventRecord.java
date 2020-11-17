/*
 * Copyright (c) 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.json.Event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

/**
 * DB entity for an event record.
 *
 * An event record consists of an ID and a JSON document with the event data.
 */
@Entity
@Table(name = "events")
public class EventRecord {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        // the following allows us to distinguish between
        // - Optional.of(null) (null value, e.g. {"foo":null}), representing a field to be cleared
        // - null (missing field, e.g. {}), representing a field that was unaffected or unknown
        OBJECT_MAPPER.registerModule(new Jdk8Module());

        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OBJECT_MAPPER.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Create a new EventRecord given Event JSON.
     *
     * Generates a new random UUID if the Event JSON does not have a UUID.
     *
     * @param json event in JSON format
     * @return EventRecord, populated with a deserialized event record
     * @throws JsonProcessingException if the jsonString cannot be processed
     */
    public static EventRecord fromJson(String json) throws JsonProcessingException {
        Event event = OBJECT_MAPPER.readValue(json, Event.class);
        return EventRecord.fromEvent(event);
    }

    public static EventRecord fromEvent(Event event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        EventRecord eventRecord = new EventRecord();
        eventRecord.setId(event.getEventId());
        eventRecord.setEvent(event);
        eventRecord.setAccountNumber(event.getAccountNumber());
        eventRecord.setTimestamp(event.getTimestamp());
        return eventRecord;
    }

    @Id
    private UUID id;

    @Column(name = "account_number")
    private String accountNumber;

    private OffsetDateTime timestamp;

    @Valid
    @Column(name = "data")
    @Convert(converter = EventRecord.Converter.class)
    private Event event;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventRecord)) {
            return false;
        }
        EventRecord that = (EventRecord) o;
        return Objects.equals(id, that.id) && Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, event);
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        }
        catch (JsonProcessingException e) {
            throw new SubscriptionsException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Error emitting event JSON",
                e
            );
        }
    }

    /**
     * JPA AttributeConverter which uses Jackson to map to/from Event JSON.
     */
    private static class Converter implements AttributeConverter<Event, String> {
        @Override
        public String convertToDatabaseColumn(Event attribute) {
            try {
                return OBJECT_MAPPER.writeValueAsString(attribute);
            }
            catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing event", e);
            }
        }

        @Override
        public Event convertToEntityAttribute(String dbData) {
            try {
                return OBJECT_MAPPER.readValue(dbData, Event.class);
            }
            catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error parsing event", e);
            }
        }
    }
}
