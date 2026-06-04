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
package com.redhat.swatch.common.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class ClowderConfigSourceWorkaround implements ConfigSource {
  private static final Map<String, String> configuration = new HashMap<>();

  static {
    // override java.class.path to placeholder to prevent it from being processed by
    // clowder-quarkus-config-source, which causes a build failure if the value gets too long
    configuration.put("java.class.path", "placeholder");
  }

  @Override
  public int getOrdinal() {
    return 500;
  }

  @Override
  public Set<String> getPropertyNames() {
    return configuration.keySet();
  }

  @Override
  public String getValue(final String propertyName) {
    return configuration.get(propertyName);
  }

  @Override
  public String getName() {
    return ClowderConfigSourceWorkaround.class.getSimpleName();
  }
}
