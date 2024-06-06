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
package com.redhat.swatch.faulttolerance.interceptors;

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import io.quarkus.arc.AbstractAnnotationLiteral;
import io.quarkus.arc.runtime.InterceptorBindings;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.faulttolerance.api.FaultTolerance;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

@RetryWithExponentialBackoff
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE + 1)
public class RetryWithExponentialBackoffInterceptor {

  private static final Logger LOGGER =
      Logger.getLogger(RetryWithExponentialBackoffInterceptor.class);
  private static final String PROPERTY_START = "${";
  private static final String PROPERTY_END = "}";
  private static final String PROPERTY_DEFAULT = ":";

  private final Config config;

  public RetryWithExponentialBackoffInterceptor(Config config) {
    this.config = config;
  }

  @AroundInvoke
  public Object intercept(InvocationContext context) throws Exception {
    Optional<RetryWithExponentialBackoff> interceptionContext =
        getArcCacheInterceptionContext(context);
    if (interceptionContext.isEmpty()) {
      return context.proceed();
    }

    var binding = interceptionContext.get();
    var retry = FaultTolerance.create().withRetry();
    getOptionalValue(binding.maxRetries(), Integer.class).ifPresent(retry::maxRetries);
    var duration = getOptionalValue(binding.delay(), Duration.class);
    if (duration.isPresent()) {
      retry.delay(duration.get().toMillis(), ChronoUnit.MILLIS);
    }

    var exponentialBackoff = retry.withExponentialBackoff();
    getOptionalValue(binding.factor(), Integer.class).ifPresent(exponentialBackoff::factor);
    getOptionalValue(binding.maxDelay(), Duration.class)
        .ifPresent(maxDelay -> exponentialBackoff.maxDelay(maxDelay.toMillis(), ChronoUnit.MILLIS));
    retry = exponentialBackoff.done();

    return retry.done().build().call(context::proceed);
  }

  @SuppressWarnings("unchecked")
  private <T> Optional<T> getOptionalValue(String property, Class<T> clazz) {
    if (!isNotEmpty(property)) {
      return Optional.empty();
    }

    if (!hasComputedProperties(property)) {
      // it's not a computed property, but directly the value, we need to only cast it.
      return Optional.of((T) parseValue(property, clazz));
    }

    List<String> computedProperties = getComputedProperties(property);
    for (String rawComputedProperty : computedProperties) {
      T value = null;
      String computedProperty = rawComputedProperty;
      if (rawComputedProperty.contains(PROPERTY_DEFAULT)) {
        int splitPosition = computedProperty.indexOf(PROPERTY_DEFAULT);
        String valueAsStr = computedProperty.substring(splitPosition + PROPERTY_DEFAULT.length());
        computedProperty = computedProperty.substring(0, splitPosition);

        if (hasComputedProperties(valueAsStr)) {
          value = getOptionalValue(valueAsStr, clazz).orElse(null);
        } else {
          value = (T) parseValue(valueAsStr, clazz);
        }
      }

      Optional<T> optionalValue = config.getOptionalValue(computedProperty, clazz);
      if (optionalValue.isPresent()) {
        // found the value in the configuration:
        return optionalValue;
      } else if (value != null) {
        // otherwise take the default value:
        return Optional.of(value);
      }
    }

    return Optional.empty();
  }

  private Optional<RetryWithExponentialBackoff> getArcCacheInterceptionContext(
      InvocationContext invocationContext) {
    Set<AbstractAnnotationLiteral> bindings =
        InterceptorBindings.getInterceptorBindingLiterals(invocationContext);
    if (bindings == null) {
      LOGGER.trace("Interceptor bindings not found in ArC");
      // This should only happen when the interception is not managed by Arc.
      return Optional.empty();
    }

    for (AbstractAnnotationLiteral binding : bindings) {
      if (binding.annotationType().isAssignableFrom((RetryWithExponentialBackoff.class))) {
        return Optional.of((RetryWithExponentialBackoff) binding);
      }
    }

    return Optional.empty();
  }

  private static Object parseValue(String value, Class<?> clazz) {
    if (String.class == clazz) return value;
    if (Duration.class == clazz) return DurationConverter.parseDuration(value);
    if (Boolean.class == clazz) return Boolean.parseBoolean(value);
    if (Integer.class == clazz) return Integer.parseInt(value);
    if (Long.class == clazz) return Long.parseLong(value);
    if (Float.class == clazz) return Float.parseFloat(value);
    if (Double.class == clazz) return Double.parseDouble(value);
    throw new UnsupportedOperationException(
        "Unsupported type '" + clazz + "' in @RetryWithExponentialBackoff");
  }

  private static boolean hasComputedProperties(String rawValue) {
    return isNotEmpty(rawValue) && rawValue.contains(PROPERTY_START);
  }

  private static List<String> getComputedProperties(String str) {
    if (!isNotEmpty(str)) {
      return List.of();
    }

    int closeLen = PROPERTY_END.length();
    int openLen = PROPERTY_START.length();
    List<String> list = new ArrayList<>();
    int end;
    for (int pos = 0; pos < str.length() - closeLen; pos = end + closeLen) {
      int start = str.indexOf(PROPERTY_START, pos);
      end = str.indexOf(PROPERTY_END, start);
      if (start < 0 || end < 0) {
        break;
      }

      start += openLen;

      String currentStr = str.substring(start);
      String tentative = currentStr.substring(0, end - start);
      while (countMatches(tentative, PROPERTY_START) != countMatches(tentative, PROPERTY_END)) {
        end++;
        if (end >= str.length()) {
          break;
        }

        tentative = currentStr.substring(0, end - start);
      }

      list.add(tentative);
    }

    return list;
  }

  private static boolean isNotEmpty(String str) {
    return str != null && !str.isEmpty();
  }

  private static int countMatches(String str, String sub) {
    if (isNotEmpty(str) && isNotEmpty(sub)) {
      int count = 0;

      for (int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sub.length()) {
        ++count;
      }

      return count;
    } else {
      return 0;
    }
  }
}
