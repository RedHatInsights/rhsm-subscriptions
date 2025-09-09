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
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public final class ServiceConfigurationLoader {

  private static final String GLOBAL_PROPERTIES =
      System.getProperty(
          "swatch.component-tests.test.resources.file.location", "global.properties");
  private static final String TEST_PROPERTIES = "test.properties";
  private static final String PREFIX_TEMPLATE = "swatch.component-tests.services.%s.";
  private static final String ALL_SERVICES = "all";

  private ServiceConfigurationLoader() {}

  public static <T extends Annotation, C> C load(
      String scope, ComponentTestContext context, BaseConfigurationBuilder<T, C> builder) {
    Map<String, String> properties = new HashMap<>();
    // Lowest priority: properties from global.properties and scope `global`
    properties.putAll(loadPropertiesFrom(GLOBAL_PROPERTIES, ALL_SERVICES));
    // Then, system properties with scope global
    properties.putAll(loadPropertiesFromSystemProperties(ALL_SERVICES));

    // Then, properties from test.properties and scope as service name
    properties.putAll(loadPropertiesFrom(TEST_PROPERTIES, scope));
    // Then, highest priority: properties from system properties and scope as service name
    properties.putAll(loadPropertiesFromSystemProperties(scope));

    // Load configuration from annotations
    builder.with(scope, context).withProperties(properties);

    // Build service configuration mixing up configuration from properties and annotations
    return builder.build();
  }

  private static Map<String, String> loadPropertiesFromSystemProperties(String scope) {
    return loadPropertiesFrom(System.getProperties(), scope);
  }

  private static Map<String, String> loadPropertiesFrom(String propertiesFile, String scope) {
    try (InputStream input =
        ServiceConfigurationLoader.class.getClassLoader().getResourceAsStream(propertiesFile)) {
      Properties prop = new Properties();
      prop.load(input);
      return loadPropertiesFrom(prop, scope);
    } catch (Exception ignored) {
      // There is no properties file: this is not mandatory.
    }

    return Collections.emptyMap();
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
