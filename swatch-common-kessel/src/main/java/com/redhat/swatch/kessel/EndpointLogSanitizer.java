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
package com.redhat.swatch.kessel;

import java.net.URI;
import java.util.function.Supplier;

/** Only expose a destination's scheme, hostname, and port in operational logs. */
public final class EndpointLogSanitizer {
  private EndpointLogSanitizer() {}

  public static String resolve(Supplier<String> endpoint) {
    try {
      return destination(endpoint.get());
    } catch (RuntimeException e) {
      // Diagnostics must not initialize clients, expose a failed expression, or break startup.
      return "<unresolved>";
    }
  }

  public static String destination(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return "<not-configured>";
    }
    try {
      URI uri = URI.create(endpoint.contains("://") ? endpoint : "//" + endpoint);
      if ("dns".equals(uri.getScheme()) && uri.getRawAuthority() == null) {
        return "dns:///" + hostAndPort(URI.create("//" + uri.getRawPath().substring(1)));
      }
      if (uri.getScheme() != null
          && !"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        return "<unresolved>";
      }
      return (uri.getScheme() == null ? "" : uri.getScheme() + "://") + hostAndPort(uri);
    } catch (IllegalArgumentException e) {
      // Never include the original value or exception: either could contain credentials.
      return "<unresolved>";
    }
  }

  private static String hostAndPort(URI uri) {
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("Endpoint hostname is unavailable");
    }
    return uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
  }
}
