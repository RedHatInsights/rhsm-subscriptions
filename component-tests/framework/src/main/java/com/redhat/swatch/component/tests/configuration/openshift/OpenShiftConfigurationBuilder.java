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

import com.redhat.swatch.component.tests.api.RunOnOpenShift;
import com.redhat.swatch.component.tests.configuration.BaseConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import java.util.Optional;

public final class OpenShiftConfigurationBuilder
    extends BaseConfigurationBuilder<RunOnOpenShift, OpenShiftConfiguration> {

  private static final String PRINT_INFO_ON_ERROR = "print.info.on.error";
  private static final String ADDITIONAL_RESOURCES = "additional-resources";

  @Override
  public OpenShiftConfiguration build() {
    OpenShiftConfiguration config = new OpenShiftConfiguration();
    loadBoolean(PRINT_INFO_ON_ERROR, a -> a.printInfoOnError())
        .ifPresent(config::setPrintInfoOnError);
    loadArrayOfStrings(ADDITIONAL_RESOURCES, a -> a.additionalResources())
        .ifPresent(config::setAdditionalResources);
    return config;
  }

  @Override
  protected Optional<RunOnOpenShift> getAnnotationConfig(
      String serviceName, ComponentTestContext context) {
    return context.getAnnotatedConfiguration(RunOnOpenShift.class);
  }
}
