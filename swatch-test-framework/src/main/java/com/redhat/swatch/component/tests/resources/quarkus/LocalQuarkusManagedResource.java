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
package com.redhat.swatch.component.tests.resources.quarkus;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.MANAGEMENT_PORT;
import static com.redhat.swatch.component.tests.utils.SwatchUtils.SERVER_PORT_PROPERTY;

import com.redhat.swatch.component.tests.configuration.quarkus.QuarkusServiceConfiguration;
import com.redhat.swatch.component.tests.configuration.quarkus.QuarkusServiceConfigurationBuilder;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class LocalQuarkusManagedResource extends ManagedResource {

  private static final List<String> ERRORS =
      Arrays.asList(
          "Failed to start application",
          "Failed to load config value of type class",
          "Quarkus may already be running or the port is used by another application",
          "One or more configuration errors have prevented the application from starting",
          "Attempting to start live reload endpoint to recover from previous Quarkus startup failure",
          "Dev mode process did not complete successfully");

  private static final String MANAGEMENT_PORT_PROPERTY = "quarkus.management.port";
  private static final String LOCALHOST = "localhost";
  private static final String LOG_OUTPUT_FILE = "out.log";

  protected Map<String, String> propertiesToOverwrite = new HashMap<>();

  private File logOutputFile;
  private Process process;
  private LoggingHandler loggingHandler;
  private int assignedHttpPort;
  private Map<Integer, Integer> assignedCustomPorts;

  private final File location;
  private final String service;

  public LocalQuarkusManagedResource(String service) {
    this.location = Paths.get("").resolve("../..").toAbsolutePath().normalize().toFile();
    this.service = service;
  }

  @Override
  public void start() {
    if (process != null && process.isAlive()) {
      // do nothing
      return;
    }

    try {
      List<String> command = prepareCommand(getPropertiesForCommand());
      Log.info(context.getOwner(), "Running command: %s", String.join(" ", command));

      ProcessBuilder pb =
          ProcessBuilderProvider.command(command)
              .redirectErrorStream(true)
              .redirectOutput(logOutputFile)
              .directory(location);

      process = pb.start();

      loggingHandler = new FileServiceLoggingHandler(context.getOwner(), logOutputFile);
      loggingHandler.startWatching();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    if (loggingHandler != null) {
      loggingHandler.stopWatching();
    }

    ProcessUtils.destroy(process);
  }

  @Override
  public String getHost() {
    return LOCALHOST;
  }

  @Override
  public int getMappedPort(int port) {
    return Optional.ofNullable(assignedCustomPorts.get(port)).orElse(assignedHttpPort);
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
            && ERRORS.stream().anyMatch(error -> loggingHandler.logsContains(error));
  }

  public String getExpectedLog() {
    return context.getConfigurationAs(QuarkusServiceConfiguration.class).getExpectedLog();
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        QuarkusServiceConfiguration.class, new QuarkusServiceConfigurationBuilder());
    this.logOutputFile = new File(context.getServiceFolder().resolve(LOG_OUTPUT_FILE).toString());
    assignPorts();
  }

  protected List<String> getPropertiesForCommand() {
    Map<String, String> runtimeProperties = new HashMap<>(context.getOwner().getProperties());
    runtimeProperties.putAll(propertiesToOverwrite);

    return runtimeProperties.entrySet().stream()
        .map(e -> "-D" + e.getKey() + "=" + e.getValue())
        .collect(Collectors.toList());
  }

  protected List<String> prepareCommand(List<String> systemProperties) {
    List<String> command = new LinkedList<>();
    command.add("./mvnw");
    command.addAll(systemProperties);
    command.add("-pl");
    command.add(service);
    command.add("quarkus:dev");

    return command;
  }

  protected Map<Integer, Integer> assignCustomPorts() {
    Map<Integer, Integer> customPorts = new HashMap<>();
    int assignedManagementPort = getOrAssignPortByProperty(MANAGEMENT_PORT_PROPERTY);
    propertiesToOverwrite.put(MANAGEMENT_PORT_PROPERTY, "" + assignedManagementPort);
    customPorts.put(MANAGEMENT_PORT, assignedManagementPort);
    return customPorts;
  }

  protected int getOrAssignPortByProperty(String property) {
    return context
        .getOwner()
        .getProperty(property)
        .filter(StringUtils::isNotEmpty)
        .map(Integer::parseInt)
        .orElseGet(() -> SocketUtils.findAvailablePort(context.getOwner()));
  }

  private void assignPorts() {
    assignedHttpPort = getOrAssignPortByProperty(SERVER_PORT_PROPERTY);
    propertiesToOverwrite.put(SERVER_PORT_PROPERTY, "" + assignedHttpPort);

    this.assignedCustomPorts = assignCustomPorts();
  }
}
