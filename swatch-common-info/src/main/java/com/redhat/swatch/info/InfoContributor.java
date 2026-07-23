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
package com.redhat.swatch.info;

/**
 * Contributes a named section to the management {@code /info} endpoint.
 *
 * <p>Unlike Quarkus {@code quarkus-info} contributors (evaluated at startup), {@link #data()} is
 * invoked on every request so services can wire runtime state (for example feature flags).
 */
public interface InfoContributor {

  /** Top-level JSON key for this contribution (for example {@code feature-flags}). */
  String name();

  /**
   * Section payload evaluated at request time. {@code null} or empty maps/collections are omitted
   * from the response.
   */
  Object data();
}
