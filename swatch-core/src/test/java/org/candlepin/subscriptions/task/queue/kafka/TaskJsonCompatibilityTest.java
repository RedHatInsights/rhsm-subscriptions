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
package org.candlepin.subscriptions.task.queue.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

class TaskJsonCompatibilityTest {
  // Preserve the JSON and type header contract used before the Jackson 3 migration.
  private static final String LEGACY_JSON =
      """
      {"groupId":"tally","type":"UPDATE","args":{"org_id":["123"]}}
      """;
  private static final String LEGACY_TYPE = "org.candlepin.subscriptions.task.JsonTaskMessage";
  private final JsonTaskMessage message =
      new JsonTaskMessage("tally", "UPDATE", Map.of("org_id", List.of("123")));

  @Test
  void configuredConsumerReadsLegacyMessagesAndTypeHeaders() {
    var factory =
        new KafkaConfigurator(new KafkaConsumerRegistry())
            .defaultConsumerFactory(new KafkaProperties());
    try (var deserializer = new ErrorHandlingDeserializer<JsonTaskMessage>()) {
      deserializer.configure(factory.getConfigurationProperties(), false);
      var headers = new RecordHeaders();
      headers.add("__TypeId__", LEGACY_TYPE.getBytes(StandardCharsets.UTF_8));
      var bytes = LEGACY_JSON.getBytes(StandardCharsets.UTF_8);
      assertEquals(message, deserializer.deserialize("tasks", headers, bytes));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void configuredProducerPreservesLegacyJsonAndTypeHeaders() throws Exception {
    var properties = KafkaTaskProducerConfiguration.getProducerProperties(new KafkaProperties());
    var type =
        (Class<? extends Serializer<JsonTaskMessage>>)
            properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
    try (var serializer = type.getConstructor().newInstance()) {
      assertInstanceOf(JacksonJsonSerializer.class, serializer);
      serializer.configure(properties, false);
      var headers = new RecordHeaders();
      var bytes = serializer.serialize("tasks", headers, message);
      var mapper = JsonMapper.builder().build();
      assertEquals(mapper.readTree(LEGACY_JSON), mapper.readTree(bytes));
      assertEquals(
          LEGACY_TYPE,
          new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8));
    }
  }
}
