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
package org.candlepin.subscriptions.clowder;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/**
 * Accessor class to pull values from the Clowder JSON file. Values are queried using the <a
 * href="http://tools.ietf.org/html/draft-ietf-appsawg-json-pointer-03">JSON Pointer</a>
 * specification.
 *
 * <p>Jackson has the, in my opinion, odd behavior of returning a <i>default</i> value if the JSON
 * Pointer can't be resolved into the requested data type. This class disagrees with that decision
 * and instead throws exceptions if the data can not be converted to the requested type.
 */
public class ClowderJson {
  public static final String DEFAULT_LOCATION = "/cdapp/cdappconfig.json";

  private final JsonNode root;
  private final ObjectMapper mapper;

  public ClowderJson(InputStream s, ObjectMapper mapper) throws IOException {
    this.root = mapper.readTree(s);
    this.mapper = mapper;
  }

  public String getString(JsonPointer jsonPtr) {
    JsonNode node = root.at(jsonPtr);
    if (node.isTextual()) {
      return node.asText();
    }
    throw new IllegalStateException(jsonPtr + " does not point to a node of type String");
  }

  public boolean getBoolean(JsonPointer jsonPtr) {
    JsonNode node = root.at(jsonPtr);
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    throw new IllegalStateException(jsonPtr + " does not point to a node of type boolean");
  }

  public int getInteger(JsonPointer jsonPtr) {
    JsonNode node = root.at(jsonPtr);
    if (node.canConvertToInt()) {
      return node.asInt();
    }
    throw new IllegalStateException(jsonPtr + " does not point to a node of type int");
  }

  public long getLong(JsonPointer jsonPtr) {
    JsonNode node = root.at(jsonPtr);
    if (node.canConvertToLong()) {
      return node.asLong();
    }
    throw new IllegalStateException(jsonPtr + " does not point to a node of type long");
  }

  public double getDouble(JsonPointer jsonPtr) {
    JsonNode node = root.at(jsonPtr);
    if (node.isDouble()) {
      return node.asDouble();
    }
    throw new IllegalStateException(jsonPtr + " does not point to a node of type double");
  }

  public byte[] getBytes(JsonPointer jsonPtr) {
    try {
      byte[] bytes = root.at(jsonPtr).binaryValue();
      if (bytes == null) {
        throw new IOException("No bytes present");
      }
      return bytes;
    } catch (IOException e) {
      throw new IllegalStateException(jsonPtr + " does not point to a node of type bytes[]", e);
    }
  }

  public <T> T getType(Class<T> clazz) throws IOException {
    return mapper.readValue(mapper.treeAsTokens(root), clazz);
  }
}
