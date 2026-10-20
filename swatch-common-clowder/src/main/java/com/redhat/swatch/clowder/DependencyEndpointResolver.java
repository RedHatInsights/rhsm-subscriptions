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
package com.redhat.swatch.clowder;

import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the effective Clowder dependency endpoint for the process lifetime. Starts from config-time
 * selection and can switch to V1 after a runtime V2 connectivity failure when mode is {@code auto}.
 */
public class DependencyEndpointResolver {

  private final String name;
  private final DependencyEndpointSelection v1Selection;
  private final AtomicReference<DependencyEndpointSelection> effective = new AtomicReference<>();

  public DependencyEndpointResolver(
      String name, String mode, DependencyEndpoint v1, DependencyEndpoint v2) {
    this.name = name;
    this.v1Selection = DependencyEndpointSelection.v1Endpoint(v1);
    this.effective.set(DependencyEndpointSelection.select(mode, v1, v2));
  }

  public String name() {
    return name;
  }

  public DependencyEndpointSelection current() {
    return effective.get();
  }

  /**
   * Switches the effective endpoint to V1 when runtime fallback is allowed. Returns {@code true}
   * only when the switch happens (so the caller can retry once).
   */
  public boolean tryFallbackToV1() {
    DependencyEndpointSelection current = effective.get();
    if (current == null
        || !MODE_AUTO.equals(current.mode())
        || !VERSION_V2.equals(current.version())) {
      return false;
    }
    if (v1Selection == null || v1Selection.uri() == null || v1Selection.uri().isBlank()) {
      return false;
    }
    return effective.compareAndSet(current, v1Selection);
  }
}
