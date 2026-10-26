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
package com.redhat.swatch.clients;

public enum ClientConfigMode {
  AUTO,
  V1,
  V2;

  /** Parses Clowder/env mode ({@code auto}, {@code v1}, {@code v2}); blank → {@link #AUTO}. */
  public static ClientConfigMode from(String mode) {
    if (mode == null || mode.isBlank()) {
      return AUTO;
    }
    return ClientConfigMode.valueOf(mode.trim().toUpperCase());
  }
}
