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
package org.candlepin.subscriptions.db.model.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.candlepin.subscriptions.json.Event;
import tools.jackson.core.JacksonException;

/**
 * JPA AttributeConverter which uses Jackson to map to/from Event JSON. Note: This is NOT a
 * Spring @Component - Hibernate instantiates it directly. Uses Jackson3ObjectMapperHolder to access
 * the Jackson 3 ObjectMapper.
 */
@Converter(autoApply = false)
public class EventRecordConverter implements AttributeConverter<Event, String> {

  // No-arg constructor required for Hibernate instantiation
  public EventRecordConverter() {}

  @Override
  public String convertToDatabaseColumn(Event attribute) {
    try {
      return Jackson3ObjectMapperHolder.getInstance().writeValueAsString(attribute);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Error serializing event", e);
    }
  }

  @Override
  public Event convertToEntityAttribute(String dbData) {
    try {
      return Jackson3ObjectMapperHolder.getInstance().readValue(dbData, Event.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Error parsing event", e);
    }
  }
}
