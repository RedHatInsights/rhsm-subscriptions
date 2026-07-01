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

import java.util.Arrays;
import java.util.List;

public final class Ports {

  public static final int DEFAULT_HTTP_PORT = 8080;
  public static final int DEFAULT_SSL_PORT = 8443;
  public static final int ARTEMIS_PORT = 5672;
  public static final List<Integer> SSL_PORTS = Arrays.asList(DEFAULT_SSL_PORT, 443);

  private Ports() {}

  public static boolean isSsl(int port) {
    return SSL_PORTS.contains(port);
  }
}
