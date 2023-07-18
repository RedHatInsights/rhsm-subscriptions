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

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.MDC;

/** ParamConverterProvider to enable use of enums in query params. */
public class EnumParamConverterProvider implements ParamConverterProvider {
  public static final String INVALID_VALUE_EXCEPTION_MSG = "%s is not a valid value for %s";

  @SuppressWarnings("linelength")
  @Override
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (!rawType.isEnum()) {
      return null;
    }

    return new EnumParamConverter<>(rawType);
  }

  /**
   * ParamConverter that case-insensitively matches enums
   *
   * @param <T> the enum type
   */
  public static class EnumParamConverter<T> implements ParamConverter<T> {

    private final Class<T> className;
    private final Map<String, T> stringToEnumMap;

    public EnumParamConverter(Class<T> className) {
      this.className = className;

      stringToEnumMap =
          Arrays.stream(className.getEnumConstants())
              .collect(Collectors.toMap(value -> value.toString().toLowerCase(), value -> value));
    }

    public T fromString(String value) {
      if (Objects.isNull(value)) {
        return null;
      }

      String lookupValue = value.toLowerCase();

      T result = stringToEnumMap.get(lookupValue);
      if (result != null) {
        return result;
      } else {
        // Combined with our logging configuration, this tells the OnMdcEvaluator class to suppress
        // the stacktrace
        MDC.put("INVALID_" + className.getSimpleName().toUpperCase(), Boolean.TRUE.toString());
        throw new IllegalArgumentException(
            String.format(INVALID_VALUE_EXCEPTION_MSG, value, className));
      }
    }

    @Override
    public String toString(T value) {
      return Objects.nonNull(value) ? value.toString() : null;
    }
  }
}
