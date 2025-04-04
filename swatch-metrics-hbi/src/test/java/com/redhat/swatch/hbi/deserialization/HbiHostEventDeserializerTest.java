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
package com.redhat.swatch.hbi.deserialization;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.redhat.swatch.hbi.config.Channels.In;
import com.redhat.swatch.hbi.dto.HbiEvent;
import com.redhat.swatch.hbi.dto.HbiHost;
import com.redhat.swatch.hbi.dto.HbiHostCreateUpdateEventDTO;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HbiHostEventDeserializerTest {

  private Deserializer<HbiEvent> deserializer;

  @BeforeEach
  void setUp() {
    deserializer = new HbiHostEventDeserializer();
  }

  @Test
  @DisplayName("should deserialize valid HbiHostCreateUpdateEvent JSON payload")
  void shouldDeserializeValidJson() throws Exception {

    HbiHostCreateUpdateEventDTO inputEvent = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    host.setId(UUID.randomUUID());
    host.setDisplayName("demo-host");
    inputEvent.setHost(host);

    ObjectMapper objectMapper = new ObjectMapper();
    byte[] jsonBytes = objectMapper.writeValueAsBytes(inputEvent);

    HbiEvent result = deserializer.deserialize(In.HBI_HOST_EVENTS, jsonBytes);

    assertNotNull(result);
    assertInstanceOf(HbiHostCreateUpdateEventDTO.class, result);
    assertEquals("demo-host", ((HbiHostCreateUpdateEventDTO) result).getHost().getDisplayName());
  }

  @Test
  @DisplayName("should wrap MismatchedInputException for empty payload")
  void shouldWrapExceptionForEmptyPayload() {

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> deserializer.deserialize(In.HBI_HOST_EVENTS, new byte[0]),
            "Expected deserializer to throw on empty payload");

    assertNotNull(thrown.getCause(), "Expected cause in RuntimeException");
    assertTrue(
        thrown.getCause() instanceof MismatchedInputException,
        "Expected MismatchedInputException but got: " + thrown.getCause());
  }

  @Test
  @DisplayName("should throw for invalid JSON input")
  void shouldWrapExceptionForInvalidJson() {

    byte[] invalid = "not-a-json".getBytes(StandardCharsets.UTF_8);

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> deserializer.deserialize(In.HBI_HOST_EVENTS, invalid),
            "Expected deserializer to wrap exception on malformed JSON");

    assertNotNull(thrown.getCause());
    assertTrue(
        thrown.getCause().getClass().getName().startsWith("com.fasterxml.jackson"),
        "Expected Jackson-related exception");
  }
}
