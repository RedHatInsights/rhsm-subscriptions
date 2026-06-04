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
package com.redhat.swatch.component.tests.configuration;

import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.utils.DurationUtils;
import com.redhat.swatch.component.tests.utils.PropertiesUtils;
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class BaseConfigurationBuilder<T extends Annotation, C> {
  private static final String COMMA = ",";

  private Map<String, String> properties = Collections.emptyMap();
  private Optional<T> annotationConfig = Optional.empty();

  public BaseConfigurationBuilder<T, C> with(String serviceName, ComponentTestContext context) {
    this.annotationConfig = getAnnotationConfig(serviceName, context);
    return this;
  }

  public BaseConfigurationBuilder<T, C> withProperties(Map<String, String> properties) {
    this.properties = properties;
    return this;
  }

  public abstract C build();

  protected abstract Optional<T> getAnnotationConfig(
      String serviceName, ComponentTestContext context);

  protected Optional<Duration> loadDuration(
      String propertyKey, Function<T, String> annotationMapper) {
    return PropertiesUtils.getAsDuration(properties, propertyKey)
        .or(
            () ->
                annotationConfig
                    .map(annotationMapper::apply)
                    .filter(StringUtils::isNotEmpty)
                    .map(DurationUtils::parse));
  }

  protected Optional<Boolean> loadBoolean(
      String propertyKey, Function<T, Boolean> annotationMapper) {
    return PropertiesUtils.getAsBoolean(properties, propertyKey)
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected Optional<String[]> loadArrayOfStrings(
      String propertyKey, Function<T, String[]> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .map(v -> v.trim().split(COMMA))
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected Optional<String> loadString(String propertyKey, Function<T, String> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected <E extends Enum<E>> Optional<E> loadEnum(
      String propertyKey, Class<E> enumType, Function<T, E> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .map(s -> Enum.valueOf(enumType, s))
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected Optional<int[]> loadArrayOfIntegers(
      String propertyKey, Function<T, int[]> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .map(v -> v.trim().split(COMMA))
        .map(
            arrayOfStrings -> {
              int[] arrayOfIntegers = new int[arrayOfStrings.length];
              for (int i = 0; i < arrayOfStrings.length; i++) {
                arrayOfIntegers[i] = Integer.parseInt(arrayOfStrings[i]);
              }

              return arrayOfIntegers;
            })
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected Optional<Integer> loadInteger(
      String propertyKey, Function<T, Integer> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .filter(StringUtils::isNotEmpty)
        .map(Integer::parseInt)
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }

  protected Optional<Double> loadDouble(String propertyKey, Function<T, Double> annotationMapper) {
    return PropertiesUtils.get(properties, propertyKey)
        .filter(StringUtils::isNotEmpty)
        .map(Double::parseDouble)
        .or(() -> annotationConfig.map(annotationMapper::apply));
  }
}
