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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface ClientInfo {

  ClientConfig getConfig();

  default String name() {
    return getConfig().getName();
  }

  default Map<String, Object> describe() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("current", getConfig().current().id());
    info.put("primary", describe(getConfig().primary()));
    Optional.ofNullable(getConfig().fallback())
        .ifPresent(fallback -> info.put("fallback", describe(fallback)));
    return info;
  }

  private static Map<String, Object> describe(ClientConnection connection) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", connection.id());
    if (connection.hasUri()) {
      map.put("uri", parseUri(connection.uri()));
    }
    Optional.ofNullable(connection.authenticated())
        .ifPresent(authenticated -> map.put("authenticated", authenticated));
    return map;
  }

  private static String parseUri(String raw) {
    try {
      URI uri = URI.create(raw);
      if (uri.getUserInfo() == null) {
        return raw;
      }
      return new URI(
              uri.getScheme(),
              null,
              uri.getHost(),
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment())
          .toString();
    } catch (IllegalArgumentException | URISyntaxException e) {
      return "[redacted]";
    }
  }
}
