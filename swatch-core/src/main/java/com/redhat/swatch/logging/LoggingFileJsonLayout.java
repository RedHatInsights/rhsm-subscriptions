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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Logback layout for file logs consumed by the Splunk OTEL Collector sidecar: {@code message},
 * {@code severity}, and optional {@code properties} (MDC).
 */
public class LoggingFileJsonLayout extends LayoutBase<ILoggingEvent> {

  private static final String FIELD_MESSAGE = "message";
  private static final String FIELD_SEVERITY = "severity";
  private static final String FIELD_PROPERTIES = "properties";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String doLayout(ILoggingEvent event) {
    try {
      ObjectNode json = MAPPER.createObjectNode();
      json.put(FIELD_MESSAGE, formatMessage(event));
      json.put(FIELD_SEVERITY, event.getLevel().toString());

      Map<String, String> mdc = mdcProperties(event);
      if (mdc != null && !mdc.isEmpty()) {
        ObjectNode properties = json.putObject(FIELD_PROPERTIES);
        mdc.forEach(properties::put);
      }

      return MAPPER.writeValueAsString(json) + System.lineSeparator();
    } catch (Exception e) {
      addError("Failed to format file JSON log record", e);
      return null;
    }
  }

  private static Map<String, String> mdcProperties(ILoggingEvent event) {
    try {
      return event.getMDCPropertyMap();
    } catch (NullPointerException ex) {
      return null;
    }
  }

  private static String formatMessage(ILoggingEvent event) {
    event.prepareForDeferredProcessing();
    StringBuilder sb = new StringBuilder();
    sb.append(TIMESTAMP.format(Instant.ofEpochMilli(event.getTimeStamp())));
    sb.append(' ');
    sb.append(String.format("%-5s", event.getLevel()));
    sb.append(" [");
    sb.append(abbreviateLogger(event.getLoggerName()));
    sb.append("] (");
    sb.append(event.getThreadName());
    sb.append(") ");
    sb.append(event.getFormattedMessage());
    if (event.getThrowableProxy() != null) {
      sb.append(System.lineSeparator());
      sb.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
    }
    return sb.toString().stripTrailing();
  }

  private static String abbreviateLogger(String loggerName) {
    if (loggerName == null || loggerName.isEmpty()) {
      return "";
    }
    String[] parts = loggerName.split("\\.");
    if (parts.length <= 3) {
      return loggerName;
    }
    StringBuilder abbreviated = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      if (i > 0) {
        abbreviated.append('.');
      }
      if (!parts[i].isEmpty()) {
        abbreviated.append(parts[i].charAt(0));
      }
    }
    abbreviated.append('.').append(parts[parts.length - 1]);
    return abbreviated.toString();
  }
}
