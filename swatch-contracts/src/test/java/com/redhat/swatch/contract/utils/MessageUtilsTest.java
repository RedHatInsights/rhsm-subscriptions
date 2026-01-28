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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageUtilsTest {

  @Test
  void shouldConvertStringToString() {
    String input = "test message";
    String result = MessageUtils.toString(input);
    assertEquals(input, result);
  }

  @Test
  void shouldConvertUtf8BytesToString() {
    String expected = "test message with UTF-8 chars: café";
    byte[] input = expected.getBytes(StandardCharsets.UTF_8);

    String result = MessageUtils.toString(input);

    assertEquals(expected, result);
  }

  @Test
  void shouldConvertSerializedStringToString() throws Exception {
    String expected = "test serialized message";

    // Serialize the string to byte array
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(expected);
    oos.flush();
    byte[] serializedBytes = baos.toByteArray();

    String result = MessageUtils.toString(serializedBytes);

    assertEquals(expected, result);
  }

  @Test
  void shouldHandleJsonInBytes() {
    String jsonMessage = "{\"test\": \"value\", \"number\": 123}";
    byte[] input = jsonMessage.getBytes(StandardCharsets.UTF_8);

    String result = MessageUtils.toString(input);

    assertEquals(jsonMessage, result);
  }

  @Test
  void shouldReturnNullForNullInput() {
    String result = MessageUtils.toString(null);
    assertNull(result);
  }

  @Test
  void shouldReturnNullForUnsupportedType() {
    Integer unsupportedType = 123;
    String result = MessageUtils.toString(unsupportedType);
    assertNull(result);
  }

  @Test
  void shouldHandleEmptyString() {
    String input = "";
    String result = MessageUtils.toString(input);
    assertEquals("", result);
  }

  @Test
  void shouldHandleEmptyByteArray() {
    byte[] input = new byte[0];
    String result = MessageUtils.toString(input);
    assertEquals("", result);
  }
}
