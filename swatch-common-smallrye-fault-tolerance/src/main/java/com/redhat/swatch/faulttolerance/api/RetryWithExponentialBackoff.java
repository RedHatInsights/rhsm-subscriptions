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
package com.redhat.swatch.faulttolerance.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.eclipse.microprofile.faulttolerance.Retry;

@InterceptorBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface RetryWithExponentialBackoff {
  /**
   * The maximum number of retries. -1 means the maximum number of retries is unlimited. It supports
   * properties bindings like "${prop.value:defaultNumber}" or "${prop.value}" or directly the value
   * as "12". By default, the default number of retries is defined by {@link Retry#maxRetries()}.
   */
  @Nonbinding
  String maxRetries() default "";

  /**
   * The delay between retry attempts in duration format. It supports properties bindings like
   * "${prop.value:defaultDuration}" or "${prop.value}" or directly the value as "1s" (1 second). By
   * default, the default number of retries is defined by {@link Retry#delay()}.
   */
  @Nonbinding
  String delay() default "";

  /**
   * The multiplication factor for the delays in subsequent delays. It supports properties bindings
   * like "${prop.value:defaultNumber}" or "${prop.value}" or directly the value as "3". By default,
   * the default number of retries is defined by {@link ExponentialBackoff#factor()}.
   */
  @Nonbinding
  String factor() default "";

  /**
   * The maximum delay duration. It supports properties bindings like
   * "${prop.value:defaultDuration}" or "${prop.value}" or directly the value as "1m" (1 minute). By
   * default, the default number of retries is defined by {@link ExponentialBackoff#maxDelay()}.
   */
  @Nonbinding
  String maxDelay() default "";
}
