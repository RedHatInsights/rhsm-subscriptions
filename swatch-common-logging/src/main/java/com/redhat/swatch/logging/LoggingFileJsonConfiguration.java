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

import io.quarkus.bootstrap.logging.QuarkusDelayedHandler;
import io.quarkus.logging.json.runtime.JsonFormatter;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logmanager.handlers.FileHandler;

/** Attaches {@link LoggingFileJsonFormatter} to Quarkus file log handlers at startup. */
@ApplicationScoped
public class LoggingFileJsonConfiguration {

  private static final Logger LOG = Logger.getLogger(LoggingFileJsonConfiguration.class);

  @ConfigProperty(name = "LOGGING_FILE_LOG_ENABLED", defaultValue = "false")
  boolean loggingFileLogEnabled;

  void onStart(@Observes StartupEvent event) { // NOSONAR
    if (!loggingFileLogEnabled) {
      return;
    }
    for (Handler handler : findFileHandlers()) {
      replaceFormatter(handler);
    }
  }

  private void replaceFormatter(Handler handler) {
    Formatter formatter = handler.getFormatter();
    if (formatter instanceof LoggingFileJsonFormatter) {
      return;
    }
    if (formatter instanceof JsonFormatter jsonFormatter) {
      handler.setFormatter(LoggingFileJsonFormatter.fromQuarkusJsonFormatter(jsonFormatter));
      LOG.infof("Installed LoggingFileJsonFormatter on file handler %s", handler);
    }
  }

  private static List<Handler> findFileHandlers() {
    List<Handler> fileHandlers = new ArrayList<>();
    collectHandlers(LogManager.getLogManager().getLogger("").getHandlers(), fileHandlers);
    return fileHandlers;
  }

  private static void collectHandlers(Handler[] handlers, List<Handler> fileHandlers) {
    if (handlers == null) {
      return;
    }
    for (Handler handler : handlers) {
      if (handler instanceof FileHandler) {
        fileHandlers.add(handler);
      } else if (handler instanceof QuarkusDelayedHandler delayedHandler) {
        collectHandlers(delayedHandler.getHandlers(), fileHandlers);
      }
    }
  }
}
