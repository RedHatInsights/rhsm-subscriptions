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
package com.redhat.swatch.component.tests.resources.springboot;

import static com.redhat.swatch.component.tests.utils.SwatchUtils.MANAGEMENT_PORT;

import com.redhat.swatch.component.tests.configuration.springboot.SpringBootServiceConfiguration;
import com.redhat.swatch.component.tests.configuration.springboot.SpringBootServiceConfigurationBuilder;
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

public class LocalSpringBootManagedResource extends ManagedResource {

  private static final List<String> ERRORS =
      Arrays.asList(
          "APPLICATION FAILED TO START",
          "Error starting ApplicationContext",
          "Failed to configure a DataSource",
          "Web server failed to start",
          "Port .* was already in use");

  private static final String SPRING_BOOT_RUN_ARG_PROPERTY = "spring-boot.run.arguments";
  private static final String LOCALHOST = "localhost";
  private static final String LOG_OUTPUT_FILE = "out.log";
  private static final String JVM_ARGUMENTS = "spring-boot.run.jvmArguments";

  protected Map<String, String> propertiesToOverwrite = new HashMap<>();

  private File logOutputFile;
  private Process process;
  private LoggingHandler loggingHandler;
  private int assignedHttpPort;
  private Integer assignedDebugPort;
  private Map<Integer, Integer> assignedCustomPorts;

  private final File location;
  private final String service;

  public LocalSpringBootManagedResource(String service) {
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

      loggingHandler = new FileServiceLoggingHandler(context, logOutputFile);
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
    if (super.isFailed()) {
      return true;
    }

    if (loggingHandler != null) {
      Optional<String> foundError =
          ERRORS.stream().filter(error -> loggingHandler.logsContains(error)).findFirst();

      loggingHandler.logs().forEach(line -> Log.info(context.getOwner(), line));

      if (foundError.isPresent()) {
        Log.error(
            context.getOwner(), "Service failure detected. Error found: %s", foundError.get());
        return true;
      }
    }

    return false;
  }

  public String getExpectedLog() {
    return context.getConfigurationAs(SpringBootServiceConfiguration.class).getExpectedLog();
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        SpringBootServiceConfiguration.class, new SpringBootServiceConfigurationBuilder());
    this.logOutputFile = new File(context.getServiceFolder().resolve(LOG_OUTPUT_FILE).toString());
    configureSpringProfiles();
    assignPorts();
  }

  protected List<String> getPropertiesForCommand() {
    Map<String, String> runtimeProperties = new HashMap<>(context.getOwner().getProperties());
    runtimeProperties.putAll(propertiesToOverwrite);

    return runtimeProperties.entrySet().stream()
        .map(
            e -> {
              String value = e.getValue();
              if (SPRING_BOOT_RUN_ARG_PROPERTY.equals(e.getKey()) && value.contains(" ")) {
                value = "'" + value + "'";
              }
              return "-D" + e.getKey() + "=" + value;
            })
        .collect(Collectors.toList());
  }

  protected List<String> prepareCommand(List<String> systemProperties) {
    List<String> command = new LinkedList<>();
    command.add("./mvnw");
    command.addAll(systemProperties);
    command.add("-pl");
    command.add(service);
    command.add("spring-boot:run");

    return command;
  }

  protected Map<Integer, Integer> assignCustomPorts() {
    Map<Integer, Integer> customPorts = new HashMap<>();
    int assignedManagementPort = SocketUtils.findAvailablePort(context.getOwner());
    addJvmArguments(" -Dmanagement.server.port=" + assignedManagementPort);
    customPorts.put(MANAGEMENT_PORT, assignedManagementPort);

    if (context.isDebug()) {
      assignedDebugPort = SocketUtils.findAvailablePort(context.getOwner());
      addJvmArguments(
          "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + assignedDebugPort);
    }
    return customPorts;
  }

  private void configureSpringProfiles() {
    addJvmArguments("-Dspring.profiles.active=api,worker,kafka-queue,component-test");
  }

  private void assignPorts() {
    assignedHttpPort = SocketUtils.findAvailablePort(context.getOwner());
    addJvmArguments("-Dserver.port=" + assignedHttpPort);

    this.assignedCustomPorts = assignCustomPorts();
  }

  private void addJvmArguments(String argument) {
    propertiesToOverwrite.put(
        JVM_ARGUMENTS, propertiesToOverwrite.getOrDefault(JVM_ARGUMENTS, "") + " " + argument);
  }
}
