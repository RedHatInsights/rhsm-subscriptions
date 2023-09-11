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

import com.redhat.swatch.configuration.registry.MetricId;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;

/** ParamConverterProvider to validate MetricId values against configuration. */
@Provider
public class MetricIdParamConverterProvider implements ParamConverterProvider {

  @SuppressWarnings("linelength")
  @Override
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (rawType.isAssignableFrom(MetricId.class)) {
      return (ParamConverter<T>) new MetricIdParamConverter();
    }
    return null;
  }

  /** ParamConverter that converts strings to configured MetricId values */
  public static class MetricIdParamConverter implements ParamConverter<MetricId> {

    @Override
    public MetricId fromString(String value) {
      if (Objects.isNull(value)) {
        return null;
      }
      return MetricId.fromString(value);
    }

    @Override
    public String toString(MetricId value) {
      return value.toString();
    }
  }
}
