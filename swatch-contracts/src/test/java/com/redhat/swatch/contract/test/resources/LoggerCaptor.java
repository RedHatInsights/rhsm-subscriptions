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
package com.redhat.swatch.contract.test.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.awaitility.Awaitility;
import org.jboss.logmanager.Level;

public class LoggerCaptor extends Handler {

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();

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

  public List<LogRecord> getRecords() {
    return new ArrayList<>(records);
  }

  public void thenInfoLogWithMessage(String str) {
    thenLogWithMessage(Level.INFO, str);
  }

  public void thenErrorLogWithMessage(String str) {
    thenLogWithMessage(Level.ERROR, str);
  }

  public void thenDebugLogWithMessage(String str) {
    thenLogWithMessage(Level.DEBUG, str);
  }

  public void thenLogWithMessage(Level level, String str) {
    Awaitility.await()
        .untilAsserted(
            () ->
                assertTrue(
                    records.stream()
                        .anyMatch(
                            r -> r.getLevel().equals(level) && r.getMessage().contains(str))));
  }

  public void clearRecords() {
    records.clear();
  }
}
