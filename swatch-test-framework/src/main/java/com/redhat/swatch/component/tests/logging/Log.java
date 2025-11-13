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

import static com.redhat.swatch.component.tests.utils.StringUtils.EMPTY;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.core.ComponentTestExtension;
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class Log {

  public static final String LOG_LEVEL = System.getProperty("log.level", "INFO");
  public static final String LOG_FORMAT =
      System.getProperty("log.format", "[%1$tT.%tL] [%4$s] %5$s %6$s%n");
  public static final boolean LOG_NO_COLOR =
      Boolean.parseBoolean(System.getProperty("log.disable-color", "false"));
  public static final boolean PRINT_ERROR_TO_STD_ERR =
      Boolean.parseBoolean(System.getProperty("log.print-errors-to-std-err", "false"));
  public static final String LOG_FILE_PATH = System.getProperty("log.file.path", "target/logs");

  public static final String LOG_SUFFIX = ".log";

  private static final Service NO_SERVICE = null;
  private static final String COLOR_RESET = "\u001b[0m";

  private static final String COLOR_SEVERE = "\u001b[91m";
  private static final String COLOR_WARNING = "\u001b[93m";
  private static final String COLOR_DEFAULT = "\u001b[32m";

  private static final List<String> ALL_SERVICE_COLORS =
      Arrays.asList(
          "\u001b[0;34m", // Blue
          "\u001b[0;92m", // Green
          "\u001b[0;93m", // Yellow
          "\u001b[0;94m", // Blue
          "\u001b[0;95m", // Purple
          "\u001b[0;96m" // Cyan
          );
  private static final List<String> UNUSED_SERVICE_COLORS = new ArrayList<>(ALL_SERVICE_COLORS);

  private static final Random RND = new Random();
  private static final Logger LOG = Logger.getLogger(Log.class.getName());

  private static final Map<String, String> SERVICE_COLOR_MAPPING = new HashMap<>();

  private Log() {}

  public static void info(Service service, String msg, Object... args) {
    log(service, Level.INFO, msg, args);
  }

  public static void info(String msg, Object... args) {
    log(NO_SERVICE, Level.INFO, msg, args);
  }

  public static void debug(Service service, String msg, Object... args) {
    log(service, Level.FINE, msg, args);
  }

  public static void debug(String msg, Object... args) {
    log(NO_SERVICE, Level.FINE, msg, args);
  }

  public static void trace(Service service, String msg, Object... args) {
    log(service, Level.FINEST, msg, args);
  }

  public static void trace(String msg, Object... args) {
    log(NO_SERVICE, Level.FINEST, msg, args);
  }

  public static void warn(Service service, String msg, Object... args) {
    log(service, Level.WARNING, msg, args);
  }

  public static void warn(String msg, Object... args) {
    log(NO_SERVICE, Level.WARNING, msg, args);
  }

  public static void error(Service service, String msg, Object... args) {
    log(service, Level.SEVERE, msg, args);
  }

  public static void error(String msg, Object... args) {
    log(NO_SERVICE, Level.SEVERE, msg, args);
  }

  public static void configure() {
    // Configure Log Manager
    try (InputStream in =
        ComponentTestExtension.class.getResourceAsStream("/component-tests-logging.properties")) {
      LogManager.getLogManager().readConfiguration(in);
    } catch (IOException e) {
      // ignore
    }

    String logPattern = LOG_FORMAT;
    Level level = Level.parse(LOG_LEVEL);

    // Configure logger handlers
    Logger logger = LogManager.getLogManager().getLogger(Log.class.getName());
    logger.setLevel(level);

    // Custom handlers
    logger.addHandler(new StandardDualHandler(logPattern, level));

    // - File
    try {
      FileHandler file = new FileHandler();
      file.setFormatter(new PatternSimpleFormatter(logPattern));
      file.setLevel(level);
      logger.addHandler(file);
    } catch (Exception ex) {
      Log.warn("Could not configure file handler. Caused by " + ex);
    }
  }

  private static void log(Service service, Level level, String msg, Object... args) {
    Level logLevel = level;
    if (service != null && isServiceLogLevelAllowed(service, level)) {
      logLevel = new ForceLogLevel(level);
    }

    String textColor = findColorForText(level, service);
    String logMessage = msg;
    if (args != null && args.length > 0) {
      logMessage = String.format(msg, args);
    }

    String message = inBrackets(service) + logMessage;
    if (!LOG_NO_COLOR) {
      message = textColor + message + COLOR_RESET;
    }

    LOG.log(logLevel, message);
  }

  private static boolean isServiceLogLevelAllowed(Service service, Level level) {
    if (service == null || service.getConfiguration() == null) {
      return false;
    }

    return service.getConfiguration().getLogLevel().intValue() <= level.intValue();
  }

  private static synchronized String findColorForText(Level level, Service service) {
    String textColor = findColorForService(service);
    if (level == Level.SEVERE) {
      textColor = COLOR_SEVERE;
    } else if (level == Level.WARNING) {
      textColor = COLOR_WARNING;
    }

    return textColor;
  }

  private static synchronized String findColorForService(Service service) {
    if (service == null) {
      return COLOR_DEFAULT;
    }

    String color = SERVICE_COLOR_MAPPING.get(service.getName());
    if (color == null) {
      if (UNUSED_SERVICE_COLORS.isEmpty()) {
        // reset if no more available service colors
        UNUSED_SERVICE_COLORS.addAll(ALL_SERVICE_COLORS);
      }

      int colorIdx = 0;
      if (UNUSED_SERVICE_COLORS.size() > 1) {
        colorIdx = RND.nextInt(UNUSED_SERVICE_COLORS.size() - 1);
      }

      color = UNUSED_SERVICE_COLORS.remove(colorIdx);
      SERVICE_COLOR_MAPPING.put(service.getName(), color);
    }

    return color;
  }

  private static String inBrackets(Service service) {
    if (service == null || StringUtils.isEmpty(service.getName())) {
      return EMPTY;
    }

    return String.format("[%s] ", service.getName());
  }
}
