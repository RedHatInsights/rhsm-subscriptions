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
package com.redhat.swatch.component.tests.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class PatternSimpleFormatter extends Formatter {

  private final String pattern;

  public PatternSimpleFormatter(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public String format(LogRecord record) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());

    // Use StringBuilder to avoid multiple string concatenations
    StringBuilder sourceBuilder = new StringBuilder();
    if (record.getSourceClassName() != null) {
      sourceBuilder.append(record.getSourceClassName());
      if (record.getSourceMethodName() != null) {
        sourceBuilder.append(' ').append(record.getSourceMethodName());
      }
    } else {
      sourceBuilder.append(record.getLoggerName());
    }
    String source = sourceBuilder.toString();

    String message = formatMessage(record);
    String throwable = "";
    if (record.getThrown() != null) {
      StringWriter sw = new StringWriter(512); // Pre-allocate buffer
      try (PrintWriter pw = new PrintWriter(sw)) {
        pw.println();
        record.getThrown().printStackTrace(pw);
      }
      throwable = sw.toString();
    }

    return String.format(
        pattern,
        zdt,
        source,
        record.getLoggerName(),
        record.getLevel().getName(),
        message,
        throwable);
  }
}
