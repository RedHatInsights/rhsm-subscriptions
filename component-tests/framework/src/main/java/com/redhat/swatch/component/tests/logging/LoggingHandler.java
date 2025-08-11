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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public abstract class LoggingHandler implements Closeable {

  private static final long TIMEOUT_IN_MILLIS = 4000;
  private static final String ANY = ".*";

  private Thread innerThread;
  private List<String> logs = new CopyOnWriteArrayList<>();
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
    return Collections.unmodifiableList(logs);
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

  public void onTestStarted() {}

  public void onTestStopped() {}

  protected void run() {
    while (running) {
      try {
        handle();
        Thread.sleep(TIMEOUT_IN_MILLIS);
      } catch (Exception ignored) {
        // ignored on purpose
      }
    }
  }

  protected void onLine(String line) {
    logs.add(line);
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
