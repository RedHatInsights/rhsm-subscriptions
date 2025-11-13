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

import static com.redhat.swatch.component.tests.logging.Log.PRINT_ERROR_TO_STD_ERR;

import java.io.PrintStream;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class StandardDualHandler extends Handler {

  public StandardDualHandler(String logPattern, Level level) {
    setFormatter(new PatternSimpleFormatter(logPattern));
    setLevel(level);
  }

  @Override
  public synchronized void publish(LogRecord record) {
    if (!isLoggable(record)) {
      return;
    }

    Level realLevel = getRealLevel(record);
    boolean isError = PRINT_ERROR_TO_STD_ERR && realLevel.intValue() >= Level.SEVERE.intValue();
    PrintStream targetStream = isError ? System.err : System.out;

    try {
      String message = getFormatter().format(record);
      System.out.print(message);

      if (isError) {
        targetStream.flush();
        System.err.print(message);
      }
    } catch (Exception ex) {
      System.err.println("Error printing log message: " + ex.getMessage());
    }
  }

  @Override
  public void flush() {
    System.out.flush();
    if (PRINT_ERROR_TO_STD_ERR) {
      System.err.flush();
    }
  }

  @Override
  public void close() {
    flush();
  }

  /** Extracts the real level from LogRecord, handling ForceLogLevel efficiently */
  private Level getRealLevel(LogRecord record) {
    Level level = record.getLevel();
    return (level instanceof ForceLogLevel forceLevel) ? forceLevel.getRealLevel() : level;
  }
}
