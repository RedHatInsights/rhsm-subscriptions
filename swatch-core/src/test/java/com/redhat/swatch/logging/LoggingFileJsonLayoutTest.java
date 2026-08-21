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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LoggingFileJsonLayoutTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LoggerContext context;
  private LoggingFileJsonLayout layout;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    context.setMDCAdapter(new LogbackMDCAdapter());
    layout = new LoggingFileJsonLayout();
    layout.setContext(context);
    layout.start();
  }

  @Test
  void emitsFlatJsonWithoutDefaultFields() throws Exception {
    ILoggingEvent event = captureEvent(Level.INFO, "Hello", null);

    String output = layout.doLayout(event);
    JsonNode node = MAPPER.readTree(output);

    assertEquals(2, countFields(node));
    assertTrue(node.has("message"));
    assertTrue(node.has("severity"));
    assertEquals("INFO", node.get("severity").asText());
    assertTrue(node.get("message").asText().contains("Hello"), output);
    assertFalse(node.has("properties"));
    assertFalse(node.has("timestamp"));
    assertFalse(node.has("mdc"));
  }

  @Test
  void mdcIsMappedToProperties() throws Exception {
    MDC.put("traceId", "abc123");
    try {
      ILoggingEvent event = captureEvent(Level.WARN, "With trace", MDC.getCopyOfContextMap());

      JsonNode node = MAPPER.readTree(layout.doLayout(event));

      assertTrue(node.has("properties"));
      assertEquals("abc123", node.get("properties").get("traceId").asText());
      assertEquals("WARN", node.get("severity").asText());
    } finally {
      MDC.clear();
    }
  }

  private ILoggingEvent captureEvent(
      Level level, String message, java.util.Map<String, String> mdc) {
    org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("com.redhat.swatch.test");
    Logger logger = (Logger) slf4jLogger;
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(level);
    if (mdc != null) {
      MDC.setContextMap(mdc);
    }
    try {
      if (level == Level.WARN) {
        slf4jLogger.warn(message);
      } else {
        slf4jLogger.info(message);
      }
      return appender.list.getFirst();
    } finally {
      if (mdc != null) {
        MDC.clear();
      }
      logger.detachAppender(appender);
      appender.stop();
    }
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
