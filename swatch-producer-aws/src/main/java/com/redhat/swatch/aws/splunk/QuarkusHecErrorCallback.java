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
package com.redhat.swatch.aws.splunk;

import com.splunk.logging.HttpEventCollectorErrorHandler.ErrorCallback;
import com.splunk.logging.HttpEventCollectorEventInfo;
import io.quarkus.bootstrap.logging.InitialConfigurator;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import org.jboss.logmanager.ExtHandler;

// This is a copy because quarkus's stuff is package protected.  Delete once
// https://github.com/quarkiverse/quarkus-logging-splunk/pull/111 is merged
public class QuarkusHecErrorCallback implements ErrorCallback {
  Boolean consoleEnabled;

  PrintStream stdout;

  PrintStream stderr;

  QuarkusHecErrorCallback() {
    this(System.out, System.err); // NOSONAR
  }

  /** For unit tests */
  QuarkusHecErrorCallback(PrintStream stdout, PrintStream stderr) {
    this.stdout = stdout;
    this.stderr = stderr;
  }

  /**
   * Logs the original event to stdout (if console handler is disabled). Logs the error to stderr.
   */
  @Override
  public void error(List<HttpEventCollectorEventInfo> list, Exception e) {
    final StringWriter stringWriter = new StringWriter();
    stringWriter.append("Error while sending events to Splunk HEC: ");
    stringWriter.append(e.getMessage()).append(System.lineSeparator());
    e.printStackTrace(new PrintWriter(stringWriter));
    this.stderr.println(stringWriter.toString());

    if (!isConsoleHandlerEnabled()) {
      for (HttpEventCollectorEventInfo logEvent : list) {
        this.stdout.println(logEvent.getMessage());
      }
    }
  }

  /**
   * This has to be determined lazily, as handlers are not yet registered when splunk is
   * initialized. An alternative was to check for config "quarkus.log.console.enable", but adds a
   * microprofile-config dependency.
   */
  private boolean isConsoleHandlerEnabled() {
    if (consoleEnabled == null) {
      ExtHandler delayedHandler = InitialConfigurator.DELAYED_HANDLER;
      Handler consoleHandler =
          Arrays.stream(delayedHandler.getHandlers())
              .filter(ConsoleHandler.class::isInstance)
              .findFirst()
              .orElse(null);
      consoleEnabled = (consoleHandler != null && !consoleHandler.getLevel().equals(Level.OFF));
    }
    return consoleEnabled;
  }
}
