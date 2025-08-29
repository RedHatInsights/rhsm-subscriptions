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
import java.util.Optional;
import java.util.logging.Level;

public class ServiceConfigurationBuilder
    extends BaseConfigurationBuilder<
        com.redhat.swatch.component.tests.api.configuration.ServiceConfiguration,
        ServiceConfiguration> {

  private static final String STARTUP_TIMEOUT = "startup.timeout";
  private static final String STARTUP_CHECK_POLL_INTERVAL = "startup.check-poll-interval";
  private static final String FACTOR_TIMEOUT_PROPERTY = "factor.timeout";
  private static final String LOG_ENABLED = "log.enabled";
  private static final String LOG_ENABLED_ON_TEST_STARTED = "log.enabled.on-test-started";
  private static final String LOG_LEVEL = "log.level";

  @Override
  public ServiceConfiguration build() {
    ServiceConfiguration config = new ServiceConfiguration();
    loadDuration(STARTUP_TIMEOUT, a -> a.startupTimeout()).ifPresent(config::setStartupTimeout);
    loadDuration(STARTUP_CHECK_POLL_INTERVAL, a -> a.startupCheckPollInterval())
        .ifPresent(config::setStartupCheckPollInterval);
    loadDouble(FACTOR_TIMEOUT_PROPERTY, a -> a.factorTimeout()).ifPresent(config::setFactorTimeout);
    loadBoolean(LOG_ENABLED, a -> a.logEnabled()).ifPresent(config::setLogEnabled);
    loadBoolean(LOG_ENABLED_ON_TEST_STARTED, a -> a.logEnabledOnTestStarted())
        .ifPresent(config::setLogEnabledOnTestStarted);
    loadString(LOG_LEVEL, a -> a.logLevel()).map(Level::parse).ifPresent(config::setLogLevel);
    return config;
  }

  @Override
  protected Optional<com.redhat.swatch.component.tests.api.configuration.ServiceConfiguration>
      getAnnotationConfig(String serviceName, ComponentTestContext context) {
    return context.getAnnotatedConfiguration(
        com.redhat.swatch.component.tests.api.configuration.ServiceConfiguration.class,
        a -> a.forService().equals(serviceName));
  }
}
