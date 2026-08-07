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
package com.redhat.swatch.component.tests.configuration;

import com.redhat.swatch.component.tests.core.ComponentTestContext;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public final class ConfigurationLoader {
  private static final String PREFIX_TEMPLATE = "swatch.component-tests.%s.";

  private ConfigurationLoader() {}

  public static <T extends Annotation, C> C load(
      String target, ComponentTestContext context, BaseConfigurationBuilder<T, C> builder) {
    Map<String, String> properties = new HashMap<>();
    // Then, highest priority: properties from system properties and scope as service name
    properties.putAll(loadPropertiesFromSystemProperties(target));

    // Load configuration from annotations
    builder.with(target, context).withProperties(properties);

    // Build service configuration mixing up configuration from properties and annotations
    return builder.build();
  }

  private static Map<String, String> loadPropertiesFromSystemProperties(String scope) {
    return loadPropertiesFrom(System.getProperties(), scope);
  }

  private static Map<String, String> loadPropertiesFrom(Properties prop, String scope) {
    Map<String, String> properties = new HashMap<>();
    String prefix = String.format(PREFIX_TEMPLATE, scope);
    for (Entry<Object, Object> entry : prop.entrySet()) {
      String key = (String) entry.getKey();
      if (StringUtils.startsWith(key, prefix)) {
        properties.put(key.replace(prefix, StringUtils.EMPTY), (String) entry.getValue());
      }
    }

    return properties;
  }
}
