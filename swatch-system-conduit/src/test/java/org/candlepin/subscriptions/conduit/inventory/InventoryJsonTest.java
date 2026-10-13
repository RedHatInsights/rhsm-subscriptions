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
package org.candlepin.subscriptions.conduit.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import org.candlepin.subscriptions.conduit.inventory.kafka.CreateUpdateHostMessage;
import org.candlepin.subscriptions.conduit.inventory.kafka.InventoryServiceKafkaConfigurator;
import org.candlepin.subscriptions.conduit.json.inventory.HbiHost;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class InventoryJsonTest {
  @Test
  void jackson2IsAbsentFromServiceClasspath() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"));
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.fasterxml.jackson.core.JsonFactory"));
  }

  @Test
  void sendsHbiMessagesWithIsoDatesAndWithoutEmptyProperties() {
    var mapper =
        new InventoryServiceConfiguration().hbiObjectMapper(new InventoryServiceProperties());
    var host =
        new HbiHost()
            .withDisplayName("")
            .withOrgId("123")
            .withStaleTimestamp(OffsetDateTime.parse("2026-01-02T03:04:05Z"));
    var message = new CreateUpdateHostMessage(host);
    message.setMetadata("request_id", "request-123");
    var configurator = new InventoryServiceKafkaConfigurator();
    var factory = configurator.defaultProducerFactory(new KafkaProperties(), mapper);
    var serializer = factory.getValueSerializer();
    assertInstanceOf(JacksonJsonSerializer.class, serializer);
    var json = mapper.readTree(serializer.serialize("inventory", message));
    assertEquals("add_host", json.get("operation").asText());
    assertEquals("request-123", json.get("platform_metadata").get("request_id").asText());
    assertEquals("123", json.get("data").get("org_id").asText());
    assertEquals("2026-01-02T03:04:05Z", json.get("data").get("stale_timestamp").asText());
    assertFalse(json.get("data").has("display_name"));
    assertFalse(json.get("data").has("system_profile"));
  }
}
