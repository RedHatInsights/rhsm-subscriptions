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
package com.redhat.swatch.component.tests.resources.quarkus;

import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import java.lang.annotation.Annotation;

public class QuarkusAnnotationBinding implements AnnotationBinding {

  @Override
  public boolean isFor(Annotation... annotations) {
    return findAnnotation(annotations, Quarkus.class).isPresent();
  }

  @Override
  public ManagedResource getManagedResource(
      ComponentTestContext context, Service service, Annotation... annotations) {
    Quarkus metadata = findAnnotation(annotations, Quarkus.class).get();
    if (OpenShiftExtensionBootstrap.isEnabled(context)) {
      return new OpenShiftQuarkusManagedResource(metadata.service());
    }

    // If none handler found, then the container will be running on localhost by default
    return new LocalQuarkusManagedResource(metadata.service());
  }
}
