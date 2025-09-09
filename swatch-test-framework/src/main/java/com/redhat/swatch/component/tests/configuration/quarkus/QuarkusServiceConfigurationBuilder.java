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
package com.redhat.swatch.component.tests.configuration.quarkus;

import com.redhat.swatch.component.tests.configuration.BaseConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import java.util.Optional;

public final class QuarkusServiceConfigurationBuilder
    extends BaseConfigurationBuilder<
        com.redhat.swatch.component.tests.api.configuration.QuarkusServiceConfiguration,
        QuarkusServiceConfiguration> {

  private static final String EXPECTED_OUTPUT = "quarkus.expected-log";
  private static final String DEPLOYMENT_METHOD = "quarkus.deployment-method";

  @Override
  public QuarkusServiceConfiguration build() {
    QuarkusServiceConfiguration config = new QuarkusServiceConfiguration();
    loadString(EXPECTED_OUTPUT, a -> a.expectedLog()).ifPresent(config::setExpectedLog);
    return config;
  }

  @Override
  protected Optional<
          com.redhat.swatch.component.tests.api.configuration.QuarkusServiceConfiguration>
      getAnnotationConfig(String serviceName, ComponentTestContext context) {
    return context.getAnnotatedConfiguration(
        com.redhat.swatch.component.tests.api.configuration.QuarkusServiceConfiguration.class,
        a -> a.forService().equals(serviceName));
  }
}
