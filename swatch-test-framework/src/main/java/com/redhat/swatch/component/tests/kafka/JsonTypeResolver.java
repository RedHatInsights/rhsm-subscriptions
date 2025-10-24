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
package com.redhat.swatch.component.tests.kafka;

import com.fasterxml.jackson.databind.JavaType;
import org.apache.kafka.common.header.Headers;

@FunctionalInterface
public interface JsonTypeResolver {

  /**
   * Determine the type.
   *
   * @param topic the topic.
   * @param data the serialized data.
   * @param headers the headers.
   * @return the type.
   */
  JavaType resolveType(String topic, byte[] data, Headers headers);
}
