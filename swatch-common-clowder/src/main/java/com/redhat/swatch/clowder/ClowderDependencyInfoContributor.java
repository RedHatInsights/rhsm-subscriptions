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

import com.redhat.swatch.info.InfoContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes effective Clowder dependency endpoint selections on management {@code GET /info}. Values
 * track runtime fallback. Truststore passwords and URI userinfo are omitted.
 */
@ApplicationScoped
public class ClowderDependencyInfoContributor implements InfoContributor {

  public static final String SECTION = "clowder-dependencies";

  private final Instance<DependencyEndpointResolver> resolvers;

  @Inject
  public ClowderDependencyInfoContributor(Instance<DependencyEndpointResolver> resolvers) {
    this.resolvers = resolvers;
  }

  @Override
  public String name() {
    return SECTION;
  }

  @Override
  public Object data() {
    Map<String, Object> dependencies = new LinkedHashMap<>();
    for (DependencyEndpointResolver resolver : resolvers) {
      dependencies.put(resolver.name(), toInfo(resolver.current()));
    }
    return dependencies;
  }

  static Map<String, Object> toInfo(DependencyEndpointSelection selection) {
    Map<String, Object> info = new LinkedHashMap<>();
    if (selection == null) {
      return info;
    }
    info.put("mode", selection.mode());
    info.put("version", selection.version());
    if (selection.uri() != null) {
      info.put("uri", redactUserInfo(selection.uri()));
    }
    return info;
  }

  static String redactUserInfo(String raw) {
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
