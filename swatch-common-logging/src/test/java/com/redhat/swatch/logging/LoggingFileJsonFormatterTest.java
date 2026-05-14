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
package com.redhat.swatch.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.Test;

class LoggingFileJsonFormatterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void emitsFlatJsonWithoutDefaultFields() throws Exception {
    ExtLogRecord record = new ExtLogRecord(Level.INFO, "Hello", null);
    record.setLoggerName("com.redhat.swatch.test");
    record.setThreadName("main");

    JsonNode node = MAPPER.readTree(new LoggingFileJsonFormatter().format(record));

    assertEquals(2, countFields(node));
    assertTrue(node.has("message"));
    assertTrue(node.has("severity"));
    assertEquals("INFO", node.get("severity").asText());
    assertTrue(node.get("message").asText().contains("Hello"));
    assertFalse(node.has("properties"));
    assertFalse(node.has("timestamp"));
    assertFalse(node.has("mdc"));
  }

  @Test
  void mdcIsMappedToProperties() throws Exception {
    ExtLogRecord record = new ExtLogRecord(Level.WARN, "With trace", null);
    record.setLoggerName("com.redhat.swatch.test");
    record.putMdc("traceId", "abc123");

    JsonNode node = MAPPER.readTree(new LoggingFileJsonFormatter().format(record));

    assertTrue(node.has("properties"));
    assertEquals("abc123", node.get("properties").get("traceId").asText());
    assertEquals("WARN", node.get("severity").asText());
  }

  private static long countFields(JsonNode node) {
    long count = 0;
    var fields = node.fieldNames();
    while (fields.hasNext()) {
      fields.next();
      count++;
    }
    return count;
  }
}
