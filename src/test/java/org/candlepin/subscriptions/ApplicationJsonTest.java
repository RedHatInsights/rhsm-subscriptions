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
package org.candlepin.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.db.model.converters.EventRecordConverter;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.tally.TallyWorkerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ApplicationJsonTest {
  private final ApplicationConfiguration configuration = new ApplicationConfiguration();
  private final JsonMapper mapper = configuration.objectMapper(new ApplicationProperties());

  @Test
  void preservesDateFormatsAndNullInclusion() {
    assertEquals(
        "\"2019-01-12T08:30:15.222+00:00\"",
        mapper.writeValueAsString(Date.from(Instant.parse("2019-01-12T08:30:15.222Z"))));
    var event = new Event().withTimestamp(OffsetDateTime.parse("2026-01-02T03:04:05Z"));
    var json = mapper.readTree(mapper.writeValueAsString(event));
    assertEquals("2026-01-02T03:04:05Z", json.get("timestamp").asText());
    assertFalse(json.has("display_name"));
    event.setDisplayName(Optional.empty());
    assertTrue(mapper.readTree(mapper.writeValueAsString(event)).get("display_name").isNull());
  }

  @Test
  void readsPreviouslyStoredEventsAndPreservesOptionalFields() {
    var converter = new EventRecordConverter(mapper);
    var id = UUID.fromString("99f6b275-6031-4967-84b6-147bd0191474");
    var event =
        converter.convertToEntityAttribute(
            """
        {"event_id":"99f6b275-6031-4967-84b6-147bd0191474",
         "timestamp":"2026-01-02T03:04:05Z", "display_name":null,
         "future_field":"ignored"}
        """);
    assertEquals(id, event.getEventId());
    assertEquals(Optional.empty(), event.getDisplayName());
    assertNull(event.getInventoryId());
    var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(event));
    assertEquals(event, restored);
  }

  @Test
  void wrapsMalformedStoredJson() {
    var converter = new EventRecordConverter(mapper);
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> converter.convertToEntityAttribute("{invalid"));
    assertInstanceOf(JacksonException.class, error.getCause());
  }

  @Test
  void preservesProductTagOrderAndRemovesDuplicates() {
    var event =
        mapper.readValue(
            "{\"product_tag\":[\"rosa\",\"rhel-for-x86-els-payg\",\"rosa\"]}", Event.class);
    assertInstanceOf(LinkedHashSet.class, event.getProductTag());
    assertEquals(List.of("rosa", "rhel-for-x86-els-payg"), List.copyOf(event.getProductTag()));
  }

  @Test
  void kafkaEventsRemainReadableByJackson2Consumers() {
    var event =
        new Event()
            .withEventId(UUID.randomUUID())
            .withTimestamp(OffsetDateTime.parse("2026-01-02T03:04:05Z"))
            .withDisplayName(Optional.of("host"));
    var factory =
        new TallyWorkerConfiguration().eventProducerFactory(new KafkaProperties(), mapper);
    var serializer = factory.getValueSerializer();
    assertInstanceOf(JacksonJsonSerializer.class, serializer);
    var bytes = serializer.serialize("events", event);
    var legacyMapper =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());
    try {
      assertEquals(event, legacyMapper.readValue(bytes, Event.class));
      assertTrue(legacyMapper.readTree(bytes).get("timestamp").isTextual());
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void honorsPrettyPrinting() {
    var properties = new ApplicationProperties();
    properties.setPrettyPrintJson(true);
    assertTrue(
        configuration.objectMapper(properties).writeValueAsString(new Event()).contains("\n"));
  }
}
