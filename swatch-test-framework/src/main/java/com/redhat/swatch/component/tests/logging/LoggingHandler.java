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

import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public abstract class LoggingHandler implements Closeable {

  private static final long TIMEOUT_IN_MILLIS = 4000;
  private static final String ANY = ".*";

  /** Cap retained lines so chatty OpenShift pods cannot OOM the CT runner. */
  private static final int MAX_LOG_LINES = 1_000;

  private Thread innerThread;
  private final LinkedBlockingDeque<String> logs = new LinkedBlockingDeque<>(MAX_LOG_LINES);
  private boolean running = false;

  protected abstract void handle();

  public void startWatching() {
    logs.clear();
    running = true;
    innerThread = new Thread(this::run);
    innerThread.setDaemon(true);
    innerThread.start();
  }

  public void stopWatching() {
    flush();
    running = false;
    logs.clear();
    if (innerThread != null) {
      try {
        innerThread.interrupt();
      } catch (Exception ignored) {
        // ignored on purpose.
      }
    }
  }

  public List<String> logs() {
    return List.copyOf(logs);
  }

  public boolean logsContains(String expected) {
    return logs().stream()
        .anyMatch(
            line ->
                line.contains(expected) // simple contains
                    || line.matches(ANY + expected + ANY)); // or by regular expression
  }

  public void flush() {
    AwaitilityUtils.untilAsserted(this::handle);
  }

  @Override
  public void close() {
    if (running) {
      stopWatching();
    }
  }

  public void onTestStarted() {
    logs.clear();
  }

  /**
   * Called after each test method to clear accumulated logs and reset state. Prevents memory
   * buildup across tests when services run at suite level.
   */
  public void onTestStopped() {
    logs.clear();
  }

  protected void run() {
    while (running) {
      try {
        handle();
        Thread.sleep(TIMEOUT_IN_MILLIS);
      } catch (Exception ex) {
        if (!(ex instanceof InterruptedException)) {
          Log.warn("Exception while watching logs: %s", ex);
        }
      }
    }
  }

  protected void onLine(String line) {
    if (!logs.offerLast(line)) {
      logs.pollFirst();
      logs.offerLast(line);
    }
    if (isLogEnabled()) {
      logInfo(line);
    }
  }

  protected void logInfo(String line) {
    Log.info(line);
  }

  protected void onLines(String lines) {
    Stream.of(lines.split("\\r?\\n")).filter(StringUtils::isNotEmpty).forEach(this::onLine);
  }

  protected void onStringDifference(String newLines, String oldLines) {
    if (StringUtils.isNotEmpty(oldLines)) {
      onLines(StringUtils.replace(newLines, oldLines, ""));
    } else {
      onLines(newLines);
    }
  }

  protected boolean isLogEnabled() {
    return true;
  }
}
