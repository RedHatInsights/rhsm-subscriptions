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
package com.redhat.swatch.component.tests.resources.unleash;

import com.redhat.swatch.component.tests.exceptions.ServiceNotFoundException;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.resources.containers.OpenShiftContainerManagedResource;
import com.redhat.swatch.component.tests.utils.Ports;
import java.util.Map;

public class OpenShiftUnleashContainerManagedResource extends OpenShiftContainerManagedResource {

  private static final String NAME = "unleash";
  private static final Map<String, String> LABELS = Map.of("service", "featureflags");

  private String serviceName;

  public OpenShiftUnleashContainerManagedResource() {
    // Unleash exposes REST on 4242 internally; default mapping requests 8080 -> 4242
    super(NAME, Map.of(Ports.DEFAULT_HTTP_PORT, 4242));
  }

  @Override
  protected String serviceName() {
    if (serviceName == null) {
      var services = getOpenShiftClient().servicesByLabels(LABELS);
      if (services.isEmpty()) {
        Log.error(context.getOwner(), "No services found with labels: " + LABELS);
        throw new ServiceNotFoundException(NAME);
      } else if (services.size() > 1) {
        Log.error(context.getOwner(), "Too many services found with labels: " + LABELS + ". Expected only one.");
        throw new ServiceNotFoundException(NAME);
      }

      serviceName = services.get(0).getMetadata().getName();
    }

    return serviceName;
  }

  @Override
  protected Map<String, String> podLabels() {
    return LABELS;
  }
}
