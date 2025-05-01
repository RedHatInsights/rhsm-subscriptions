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
package com.redhat.swatch.hbi.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.test.resources.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class HbiEventDeserializationTest {

  @Inject ObjectMapper objectMapper;

  private static Stream<Arguments> eventTypeDeserializationParams() {
    return Stream.of(
        Arguments.of(HbiEventTestData.getHostDeletedEvent(), "delete", HbiHostDeleteEvent.class),
        Arguments.of(
            HbiEventTestData.getPhysicalRhelHostCreatedEvent(),
            "created",
            HbiHostCreateUpdateEvent.class));
  }

  @ParameterizedTest
  @MethodSource("eventTypeDeserializationParams")
  void testHbiEventDeserialization(
      String eventJson, String expectedType, Class<?> expectedEventClass) throws Exception {
    HbiEvent hbiEvent = objectMapper.readValue(eventJson, HbiEvent.class);
    assertEquals(expectedType, hbiEvent.getType());
    assertInstanceOf(expectedEventClass, hbiEvent);
  }
}
