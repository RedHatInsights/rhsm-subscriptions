/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.resteasy;

import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

/** ParamConverterProvider to enable use of enums in query providers. */
@Component
@Provider
public class EnumParamConverterProvider implements ParamConverterProvider {

  public static final String INVALID_VALUE_EXCEPTION_MSG = "%s is not a valid value for %s";
  private static final String PACKAGE_CONTAINING_SPECIAL_ENUMS =
      "org.candlepin.subscriptions.utilization.api.model";

  @SuppressWarnings("linelength")
  @Override
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (!rawType.isEnum()) {
      return null;
    }

    return rawType.getPackage().getName().startsWith(PACKAGE_CONTAINING_SPECIAL_ENUMS)
        ? new EnumParamConverter<>(rawType, false)
        : new EnumParamConverter<>(rawType);
  }

  /**
   * ParamConverter that uses an ObjectMapper to convert enums to/from String.
   *
   * @param <T> the enum type
   */
  public static class EnumParamConverter<T> implements ParamConverter<T> {

    private final Class<T> className;
    private final Map<String, T> stringToEnumMap;
    private final boolean isCaseSensitive;

    public EnumParamConverter(Class<T> className) {
      this(className, true);
    }

    public EnumParamConverter(Class<T> className, boolean isCaseSensitive) {
      this.className = className;
      this.isCaseSensitive = isCaseSensitive;

      Function<T, String> applyToStringFunction =
          isCaseSensitive ? T::toString : t -> t.toString().toLowerCase();

      stringToEnumMap =
          Arrays.stream(className.getEnumConstants())
              .collect(Collectors.toMap(applyToStringFunction, value -> value));
    }

    public Class<T> getClassName() {
      return className;
    }

    public T fromString(String value) {
      if (Objects.isNull(value)) {
        return null;
      }

      String lookupValue = isCaseSensitive ? value : value.toLowerCase();

      T result = stringToEnumMap.get(lookupValue);
      if (result != null) {
        return result;
      }

      throw new IllegalArgumentException(
          String.format(INVALID_VALUE_EXCEPTION_MSG, value, className));
    }

    @Override
    public String toString(T value) {
      return Objects.nonNull(value) ? value.toString() : null;
    }
  }
}
