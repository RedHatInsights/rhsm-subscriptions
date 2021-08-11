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
package org.candlepin.subscriptions.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;

/**
 * Abstract class for loading data from a JSON file on the classpath or filesystem.
 *
 * @param <T> Expected return type for the loaded yaml.
 */
public abstract class JsonFileSource<T> extends StructuredFileSource<T> {
  protected ObjectMapper mapper;

  protected JsonFileSource(
      String resourceLocation, Clock clock, Duration cacheTtl, ObjectMapper mapper) {
    super(resourceLocation, clock, cacheTtl);
    this.mapper = mapper;
  }

  /**
   * Very simple implementation to deserialize a JSON object. Subclasses will likely wish to
   * redefine how the JSON for type T is deserialized.
   *
   * @param s InputStream with the JSON
   * @return an object of type T constructed from the JSON in InputStream s
   */
  protected T parse(InputStream s) throws IOException {
    return mapper.readValue(s, (Class<T>) getObjectType());
  }
}
