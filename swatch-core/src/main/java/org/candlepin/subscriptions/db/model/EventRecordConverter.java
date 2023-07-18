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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.candlepin.subscriptions.json.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** JPA AttributeConverter which uses Jackson to map to/from Event JSON. */
@Component
public class EventRecordConverter implements AttributeConverter<Event, String> {

  private static ObjectMapper objectMapper;

  public EventRecordConverter() {
    /* intentionally left empty */
  }

  // hack to get ObjectMapper from spring context, we should remove once we're on Spring Boot 2.1 &
  // Hibernate 5.3 per https://stackoverflow.com/a/54686119
  @Autowired
  @SuppressWarnings("java:S3010")
  EventRecordConverter(ObjectMapper mapper) {
    EventRecordConverter.objectMapper = mapper;
  }

  @Override
  public String convertToDatabaseColumn(Event attribute) {
    try {
      return objectMapper.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error serializing event", e);
    }
  }

  @Override
  public Event convertToEntityAttribute(String dbData) {
    try {
      return objectMapper.readValue(dbData, Event.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error parsing event", e);
    }
  }
}
