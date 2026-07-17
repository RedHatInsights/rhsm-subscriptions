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

import com.redhat.swatch.component.tests.api.clients.OpenshiftClient;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class OpenShiftLoggingHandler extends ServiceLoggingHandler {

  /**
   * Bound for per-line dedupe during a single test. Evicts eldest entries so chatty pods cannot
   * grow the dedupe set without limit.
   */
  private static final int MAX_SEEN_LINES = 1_000;

  /** Keep only the newest lines from a large {@code oc logs} chunk. */
  private static final int MAX_LINES_PER_FETCH = 1_000;

  /** Truncate retained lines; assertContains messages are short. */
  private static final int MAX_LINE_LENGTH = 2_048;

  private final OpenshiftClient client;
  private final Map<String, String> podLabels;
  private final String containerName;
  private Instant logsSince;

  /** Hash-only keys so dedupe does not retain a second copy of each log line. */
  private final Map<Integer, Boolean> seenLineHashes =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > MAX_SEEN_LINES;
        }
      };

  public OpenShiftLoggingHandler(
      Map<String, String> podLabels, String containerName, ServiceContext context) {
    super(context);

    this.podLabels = podLabels;
    this.containerName = containerName;
    this.client = context.get(OpenShiftExtensionBootstrap.CLIENT);
  }

  @Override
  public synchronized void onTestStarted() {
    super.onTestStarted();

    this.logsSince = Instant.now();
    this.seenLineHashes.clear();
  }

  @Override
  public synchronized void onTestStopped() {
    super.onTestStopped();
    this.seenLineHashes.clear();
    this.logsSince = null;
  }

  @Override
  protected synchronized void handle() {
    if (!isTestActive() || logsSince == null || !isLogEnabled()) {
      return;
    }

    Instant fetchTime = Instant.now();
    Map<String, String> newLogs = client.logsSince(podLabels, containerName, logsSince);
    this.logsSince = fetchTime;

    newLogs.forEach(this::appendNewPodLogLines);
  }

  /**
   * Pod lines stay in the bounded {@code logs} buffer for {@code assertContains}; mirroring every
   * line to INFO floods the CT runner under {@code -Dlog.level=FINE}.
   */
  @Override
  protected void logInfo(String line) {
    // intentionally empty
  }

  private void appendNewPodLogLines(String podName, String podLogs) {
    if (StringUtils.isEmpty(podLogs)) {
      return;
    }

    String[] lines = podLogs.split("\\r?\\n");
    int start = Math.max(0, lines.length - MAX_LINES_PER_FETCH);
    for (int i = start; i < lines.length; i++) {
      appendNewLine(podName, lines[i]);
    }
  }

  private void appendNewLine(String podName, String line) {
    if (StringUtils.isEmpty(line)) {
      return;
    }

    String formatted = truncate(formatPodLogs(podName, truncate(line)));
    if (seenLineHashes.put(formatted.hashCode(), Boolean.TRUE) == null) {
      onLine(formatted);
    }
  }

  private static String truncate(String line) {
    if (line.length() <= MAX_LINE_LENGTH) {
      return line;
    }
    return line.substring(0, MAX_LINE_LENGTH);
  }

  private String formatPodLogs(String podName, String log) {
    return String.format("[%s] %s", podName, log);
  }
}
