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
package com.redhat.swatch.component.tests.configuration.openshift;

import com.redhat.swatch.component.tests.configuration.BaseConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import java.util.Optional;

public final class OpenShiftServiceConfigurationBuilder
    extends BaseConfigurationBuilder<
        com.redhat.swatch.component.tests.api.configuration.OpenShiftServiceConfiguration,
        OpenShiftServiceConfiguration> {

  private static final String ADDITIONAL_PORTS_PROPERTY = "openshift.additional-ports";

  @Override
  public OpenShiftServiceConfiguration build() {
    OpenShiftServiceConfiguration config = new OpenShiftServiceConfiguration();
    loadArrayOfIntegers(ADDITIONAL_PORTS_PROPERTY, a -> a.additionalPorts())
        .ifPresent(config::setAdditionalPorts);
    return config;
  }

  @Override
  protected Optional<
          com.redhat.swatch.component.tests.api.configuration.OpenShiftServiceConfiguration>
      getAnnotationConfig(String serviceName, ComponentTestContext context) {
    return context.getAnnotatedConfiguration(
        com.redhat.swatch.component.tests.api.configuration.OpenShiftServiceConfiguration.class,
        a -> a.forService().equals(serviceName));
  }
}
