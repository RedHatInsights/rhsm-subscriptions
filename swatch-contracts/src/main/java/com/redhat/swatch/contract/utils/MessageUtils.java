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
package com.redhat.swatch.contract.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MessageUtils {
  private MessageUtils() {}

  public static String toString(Object dto) {
    String str = null;
    if (dto instanceof byte[] bytes) {
      str = deserializeBytes(bytes);
    } else if (dto instanceof String) {
      str = (String) dto;
    }

    return str;
  }

  private static String deserializeBytes(byte[] bytes) {
    // Check if bytes start with Java serialization magic bytes (0xAC 0xED)
    if (bytes.length >= 2 && bytes[0] == (byte) 0xAC && bytes[1] == (byte) 0xED) {
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
          ObjectInputStream ois = new ObjectInputStream(bis)) {
        Object obj = ois.readObject();
        if (obj instanceof String) {
          return (String) obj;
        } else {
          log.error("Deserialized object is not a String: {}", obj.getClass().getName());
          return null;
        }
      } catch (IOException | ClassNotFoundException e) {
        log.error("Failed to deserialize Java object", e);
        return null;
      }
    } else {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
