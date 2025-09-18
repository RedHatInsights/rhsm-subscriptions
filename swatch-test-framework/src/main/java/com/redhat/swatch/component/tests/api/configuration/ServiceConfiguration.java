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
package com.redhat.swatch.component.tests.api.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Repeatable(ServiceConfigurations.class)
public @interface ServiceConfiguration {
  String forService();

  /**
   * Default startup timeout for services is 5 minutes. Fallback service property:
   * "swatch.component-tests.services.SERVICE NAME.startup.timeout".
   */
  String startupTimeout() default "";

  /**
   * Default startup check poll interval is every 2 seconds. Fallback service property:
   * "swatch.component-tests.services.SERVICE NAME.startup.check-poll-interval".
   */
  String startupCheckPollInterval() default "";

  /**
   * Default timeout factor for all checks. Fallback service property:
   * "swatch.component-tests.services.SERVICE NAME.factor.timeout".
   */
  double factorTimeout() default 1.0;

  /**
   * Enable/Disable the logs for the current service. Fallback service property:
   * "swatch.component-tests.services.SERVICE NAME.log.enabled".
   */
  boolean logEnabled() default true;

  /**
   * Enable the logs for the current service only when the test starts. Fallback service property:
   * "swatch.component-tests.services.SERVICE NAME.log.enabled.on-test-started".
   */
  boolean logEnabledOnTestStarted() default true;

  /**
   * Tune the log level for the current service. Possible values in {@link java.util.logging.Level}.
   */
  String logLevel() default "INFO";
}
