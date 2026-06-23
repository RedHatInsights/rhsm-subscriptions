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
package com.redhat.swatch.contract.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import lombok.Getter;
import org.awaitility.Awaitility;
import org.jboss.logmanager.LogContext;

public class LoggerCaptor extends Handler {

  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Getter private final List<LogRecord> records = new ArrayList<>();

  @Override
  public void publish(LogRecord trace) {
    records.add(trace);
  }

  @Override
  public void flush() {
    // no need to flush any sink
  }

  @Override
  public void close() throws SecurityException {
    clearRecords();
  }

  public static void clearRecords() {
    LOGGER_CAPTOR.records.clear();
  }

  public static void registerHandler(Class<?> clazz) {
    LogContext.getLogContext().getLogger(clazz.getName()).addHandler(LOGGER_CAPTOR);
  }

  public static void thenLogNothing() {
    assertTrue(LOGGER_CAPTOR.records.isEmpty());
  }

  public static void thenInfoLogWithMessage(String str) {
    thenLogWithMessage(Level.INFO, msg -> msg.contains(str));
  }

  public static void thenErrorLogWithMessage(String str) {
    thenLogWithMessage(Level.SEVERE, msg -> msg.contains(str));
  }

  public static void thenNoErrorLogWithMessage(String str) {
    Awaitility.await()
        .untilAsserted(
            () ->
                assertTrue(
                    LOGGER_CAPTOR.records.stream()
                        .noneMatch(
                            r ->
                                (r.getLevel().equals(Level.SEVERE)
                                        || r.getLevel().equals(Level.WARNING))
                                    && messageContains(r, str))));
  }

  private static boolean messageContains(LogRecord record, String str) {
    var message = record.getMessage();
    return message != null && message.contains(str);
  }

  public static void thenWarnLogWithMessage(String str) {
    thenLogWithMessage(Level.WARNING, msg -> msg.contains(str));
  }

  public static void thenDebugLogWithMessage(String str) {
    thenLogWithMessage(Level.FINE, msg -> msg.contains(str));
  }

  private static void thenLogWithMessage(Level level, Predicate<String> check) {
    Awaitility.await()
        .untilAsserted(
            () ->
                assertTrue(
                    LOGGER_CAPTOR.records.stream()
                        .anyMatch(r -> r.getLevel().equals(level) && check.test(r.getMessage()))));
  }
}
