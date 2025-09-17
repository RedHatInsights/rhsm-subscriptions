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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public final class PropertiesUtils {

  private PropertiesUtils() {}

  public static Optional<Duration> getAsDuration(Map<String, String> properties, String property) {
    return get(properties, property).filter(StringUtils::isNotEmpty).map(DurationUtils::parse);
  }

  public static Optional<Boolean> getAsBoolean(Map<String, String> properties, String property) {
    return get(properties, property).map(Boolean::parseBoolean);
  }

  public static Optional<String> get(Map<String, String> properties, String property) {
    return Optional.ofNullable(properties.get(property));
  }

  public static Map<String, String> toMap(String propertiesFile) {
    try (InputStream in = ClassLoader.getSystemResourceAsStream(propertiesFile)) {
      return toMap(in);
    } catch (IOException e) {
      throw new RuntimeException("Could not load map from system resource", e);
    }
  }

  public static Map<String, String> toMap(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      return toMap(in);
    } catch (IOException e) {
      throw new RuntimeException("Could not load map from path", e);
    }
  }

  public static Map<String, String> toMap(InputStream is) {
    Properties properties = new Properties();
    try {
      properties.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Could not load map", e);
    }

    return (Map) properties;
  }
}
