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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.json.runtime.JsonFormatter;
import java.util.Map;
import java.util.logging.Formatter;
import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;

/**
 * JSON formatter for the Quarkus file log handler: {@code message}, {@code severity}, and optional
 * {@code properties} (MDC).
 */
public class LoggingFileJsonFormatter extends ExtFormatter {

  private static final String FIELD_MESSAGE = "message";
  private static final String FIELD_SEVERITY = "severity";
  private static final String FIELD_PROPERTIES = "properties";

  private static final String MESSAGE_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] (%t) %s%e%n";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static volatile Formatter messageFormatter;

  private String recordDelimiter = "\n";

  public static LoggingFileJsonFormatter fromQuarkusJsonFormatter(JsonFormatter source) {
    LoggingFileJsonFormatter target = new LoggingFileJsonFormatter();
    String delimiter = source.getRecordDelimiter();
    if (delimiter != null) {
      target.recordDelimiter = delimiter;
    }
    return target;
  }

  @Override
  public String format(ExtLogRecord record) {
    try {
      ObjectNode json = MAPPER.createObjectNode();
      json.put(FIELD_MESSAGE, formatMessage(record));
      json.put(FIELD_SEVERITY, record.getLevel().getName());

      Map<String, String> mdc = record.getMdcCopy();
      if (!mdc.isEmpty()) {
        ObjectNode properties = json.putObject(FIELD_PROPERTIES);
        mdc.forEach(properties::put);
      }

      return json + recordDelimiter;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to format file JSON log record", e);
    }
  }

  private static String formatMessage(ExtLogRecord record) {
    return getMessageFormatter().format(record).stripTrailing();
  }

  private static Formatter getMessageFormatter() {
    Formatter formatter = messageFormatter;
    if (formatter == null) {
      synchronized (LoggingFileJsonFormatter.class) {
        formatter = messageFormatter;
        if (formatter == null) {
          messageFormatter = new PatternFormatter(MESSAGE_FORMAT);
          formatter = messageFormatter;
        }
      }
    }
    return formatter;
  }
}
