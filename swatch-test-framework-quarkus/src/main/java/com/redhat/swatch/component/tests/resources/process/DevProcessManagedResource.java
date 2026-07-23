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
package com.redhat.swatch.component.tests.resources.process;

import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.logging.FileServiceLoggingHandler;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.logging.LoggingHandler;
import com.redhat.swatch.component.tests.utils.ProcessBuilderProvider;
import com.redhat.swatch.component.tests.utils.ProcessUtils;
import com.redhat.swatch.component.tests.utils.SocketUtils;
import java.io.File;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public abstract class DevProcessManagedResource extends ManagedResource {

  private static final String LOCALHOST = "localhost";
  private static final String LOG_OUTPUT_FILE = "out.log";

  private Process process;
  private Thread shutdownHook;
  private File logOutputFile;
  private LoggingHandler loggingHandler;

  private final File location;
  private final String service;

  public DevProcessManagedResource(String service) {
    this.location = Paths.get("").resolve("../..").toAbsolutePath().normalize().toFile();
    this.service = service;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    this.logOutputFile = new File(context.getServiceFolder().resolve(LOG_OUTPUT_FILE).toString());
  }

  @Override
  public void start() {
    if (process != null && process.isAlive()) {
      // do nothing
      return;
    }

    try {
      List<String> command = prepareCommand();
      Log.info(context.getOwner(), "Running command: %s", String.join(" ", command));

      ProcessBuilder pb =
          ProcessBuilderProvider.command(command)
              .redirectErrorStream(true)
              .redirectOutput(logOutputFile)
              .directory(location);

      process = pb.start();

      // Register shutdown hook to cleanup process if JVM is killed
      registerShutdownHook();

      // start logging to see the process traces
      loggingHandler = new FileServiceLoggingHandler(context, logOutputFile);
      loggingHandler.startWatching();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    removeShutdownHook();
    if (loggingHandler != null) {
      loggingHandler.stopWatching();
    }
    ProcessUtils.destroy(process);
  }

  @Override
  public boolean isRunning() {
    return process != null
        && process.isAlive()
        && loggingHandler != null
        && loggingHandler.logsContains(getExpectedLog());
  }

  @Override
  public boolean isFailed() {
    return super.isFailed()
        || loggingHandler != null
            && getExpectedErrorLogs().stream()
                .anyMatch(error -> loggingHandler.logsContains(error));
  }

  @Override
  public String getHost() {
    return LOCALHOST;
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  protected List<String> prepareCommand() {
    List<String> command = new LinkedList<>();
    command.add("./mvnw");
    configureCommand(command);
    // skip format checkstyle and spotless
    command.add("-Dspotless.check.skip=true");
    command.add("-Dcheckstyle.skip=true");
    command.add("-pl");
    command.add(service);
    command.add(getDevModeCommand());

    return command;
  }

  protected int getOrAssignPortByProperty(String property) {
    return context
        .getOwner()
        .getProperty(property)
        .filter(StringUtils::isNotEmpty)
        .map(Integer::parseInt)
        .orElseGet(() -> SocketUtils.findAvailablePort(context.getOwner()));
  }

  protected abstract void configureCommand(List<String> command);

  protected abstract String getDevModeCommand();

  protected abstract String getExpectedLog();

  protected abstract List<String> getExpectedErrorLogs();

  private void registerShutdownHook() {
    if (shutdownHook != null) {
      return; // Already registered
    }

    shutdownHook =
        new Thread(
            () -> {
              if (process != null && process.isAlive()) {
                Log.warn(
                    context.getOwner(),
                    "JVM shutdown detected - emergency cleanup of process PID: %d",
                    process.pid());
                ProcessUtils.destroy(process);
              }
            });

    shutdownHook.setName("DevProcessCleanup-" + service);
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  private void removeShutdownHook() {
    if (shutdownHook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      } catch (IllegalStateException e) {
        // JVM is already shutting down, ignore
      }
    }
  }
}
