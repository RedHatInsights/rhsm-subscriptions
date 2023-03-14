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
package com.redhat.swatch.common.resteasy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;

/** ParamConverterProvider to enable use of OffsetDateTime in query parameters. */
public class OffsetDateTimeParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (rawType.isAssignableFrom(OffsetDateTime.class)) {
      return (ParamConverter<T>) new OffsetDateTimeParamConverter();
    }
    return null;
  }

  /**
   * ParamConverter that uses OffsetDateTime#parse and toString to convert OffsetDateTime to/from
   * String.
   */
  public static class OffsetDateTimeParamConverter implements ParamConverter<OffsetDateTime> {

    @Override
    public OffsetDateTime fromString(String value) {
      if (value == null) {
        return null;
      }
      try {
        return OffsetDateTime.parse(value);
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(e);
      }
    }

    @Override
    public String toString(OffsetDateTime value) {
      if (value == null) {
        return null;
      }
      return value.toString();
    }
  }
}
