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
package com.redhat.swatch.component.tests.utils;

import java.util.Map;

public final class SwatchUtils {
  public static final String SERVER_PORT_PROPERTY = "SERVER_PORT";
  public static final int SERVER_PORT = 8000;
  public static final int MANAGEMENT_PORT = 9000;
  public static final Map<String, String> SECURITY_HEADERS =
      Map.of(
          "x-rh-swatch-psk", "placeholder",
          "Origin", "console.redhat.com");

  private SwatchUtils() {}
}
