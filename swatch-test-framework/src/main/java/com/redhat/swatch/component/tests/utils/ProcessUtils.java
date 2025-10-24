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
package com.redhat.swatch.component.tests.utils;

import com.redhat.swatch.component.tests.logging.Log;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.OS;

public final class ProcessUtils {

  private static final int PROCESS_KILL_TIMEOUT_MINUTES = 3;

  private ProcessUtils() {}

  public static void destroy(Process process) {
    try {
      if (process != null) {
        // First, try graceful shutdown of main process
        if (process.supportsNormalTermination()) {
          process.destroy(); // Send SIGTERM
          boolean terminated = process.waitFor(15, TimeUnit.SECONDS);
          if (terminated) {
            return; // Graceful shutdown successful
          }
        }

        // If graceful shutdown failed, proceed with forceful termination
        process
            .descendants()
            .forEach(
                child -> {
                  if (child.supportsNormalTermination()) {
                    child.destroyForcibly();
                  }

                  pidKiller(child.pid());

                  AwaitilityUtils.untilIsFalse(child::isAlive);
                });

        if (process.supportsNormalTermination()) {
          process.destroyForcibly();
          process.waitFor(PROCESS_KILL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }

        pidKiller(process.pid());
        AwaitilityUtils.untilIsFalse(process::isAlive);
      }
    } catch (Exception e) {
      Log.warn("Error trying to stop process. Caused by " + e.getMessage());
    }
  }

  private static void pidKiller(long pid) {
    try {
      if (OS.WINDOWS.isCurrentOs()) {
        Runtime.getRuntime()
            .exec(new String[] {"cmd", "/C", "taskkill", "/PID", Long.toString(pid), "/F", "/T"});
      } else {
        Runtime.getRuntime().exec(new String[] {"kill", "-9", Long.toString(pid)});
      }
    } catch (Exception e) {
      Log.warn("Error stopping process " + pid, e);
    }
  }
}
