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
package resources;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.SpringBoot;
import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import com.redhat.swatch.component.tests.resources.springboot.OpenShiftSpringBootManagedResource;
import java.lang.annotation.Annotation;

/**
 * Custom annotation binding for tally Spring Boot service that uses
 * TallyLocalSpringBootManagedResource to enable OTEL support.
 */
public class TallySpringBootAnnotationBinding implements AnnotationBinding {

  @Override
  public boolean isFor(Annotation... annotations) {
    // This binding is specifically for swatch-tally service
    // It must be more specific than the generic SpringBootAnnotationBinding
    boolean matches =
        findAnnotation(annotations, SpringBoot.class)
            .map(springBoot -> "swatch-tally".equals(springBoot.service()))
            .orElse(false);
    if (matches) {
      System.out.println("TallySpringBootAnnotationBinding matched for swatch-tally service");
    }
    return matches;
  }

  @Override
  public ManagedResource getManagedResource(
      ComponentTestContext context, Service service, Annotation... annotations) {
    java.util.Optional<SpringBoot> springBootAnnotation =
        findAnnotation(annotations, SpringBoot.class);
    if (!springBootAnnotation.isPresent()) {
      throw new IllegalArgumentException(
          "TallySpringBootAnnotationBinding requires @SpringBoot annotation");
    }

    SpringBoot metadata = springBootAnnotation.get();
    if (OpenShiftExtensionBootstrap.isEnabled(context)) {
      // For OpenShift, use the standard OpenShift resource
      return new OpenShiftSpringBootManagedResource(metadata.service());
    }

    // For local development, use our custom resource with OTEL support
    return new TallyLocalSpringBootManagedResource(metadata.service());
  }
}
