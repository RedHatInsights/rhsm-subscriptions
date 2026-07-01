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
import static com.redhat.swatch.component.tests.utils.SwatchUtils.SERVER_PORT_PROPERTY;

import com.redhat.swatch.component.tests.configuration.springboot.SpringBootServiceConfiguration;
import com.redhat.swatch.component.tests.configuration.springboot.SpringBootServiceConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.resources.process.DevProcessManagedResource;
import com.redhat.swatch.component.tests.utils.SocketUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LocalSpringBootManagedResource extends DevProcessManagedResource {

  private static final List<String> ERRORS =
      Arrays.asList(
          "APPLICATION FAILED TO START",
          "Error starting ApplicationContext",
          "Failed to configure a DataSource",
          "Web server failed to start",
          "Port .* was already in use");

  private static final String MANAGEMENT_PORT_PROPERTY = "management.server.port";
  private static final String SPRING_PROFILES_INCLUDE_PROPERTY = "spring.profiles.include";

  private static final String SERVICE_ARGUMENTS = "spring-boot.run.arguments";
  private static final String JVM_ARGUMENTS = "spring-boot.run.jvmArguments";

  protected Map<String, String> propertiesToOverwrite = new HashMap<>();

  private int assignedHttpPort;
  private Integer assignedDebugPort;
  private Map<Integer, Integer> assignedCustomPorts;

  public LocalSpringBootManagedResource(String service) {
    super(service);
  }

  @Override
  public int getMappedPort(int port) {
    return Optional.ofNullable(assignedCustomPorts.get(port)).orElse(assignedHttpPort);
  }

  public String getExpectedLog() {
    return context.getConfigurationAs(SpringBootServiceConfiguration.class).getExpectedLog();
  }

  @Override
  protected List<String> getExpectedErrorLogs() {
    return ERRORS;
  }

  @Override
  protected String getDevModeCommand() {
    return "spring-boot:run";
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        SpringBootServiceConfiguration.class, new SpringBootServiceConfigurationBuilder());
    assignPorts();
    configureSpringProfiles();
  }

  @Override
  protected void configureCommand(List<String> command) {
    // first, add the JVM arguments to debug if enabled
    if (context.isDebug()) {
      assignedDebugPort = SocketUtils.findAvailablePort(context.getOwner());
      addArguments(
          JVM_ARGUMENTS,
          "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + assignedDebugPort);
    }

    Map<String, String> runtimeProperties = new HashMap<>(context.getOwner().getProperties());
    runtimeProperties.putAll(propertiesToOverwrite);
    runtimeProperties.entrySet().stream()
        .map(e -> "-D" + e.getKey() + "=" + e.getValue())
        .forEach(command::add);
  }

  private void assignPorts() {
    assignedHttpPort = getOrAssignPortByProperty(SERVER_PORT_PROPERTY);
    addArguments(SERVICE_ARGUMENTS, "--" + SERVER_PORT_PROPERTY + "=" + assignedHttpPort);
    this.assignedCustomPorts = assignCustomPorts();
  }

  protected Map<Integer, Integer> assignCustomPorts() {
    Map<Integer, Integer> customPorts = new HashMap<>();
    int assignedManagementPort = getOrAssignPortByProperty(MANAGEMENT_PORT_PROPERTY);
    addArguments(JVM_ARGUMENTS, "-D" + MANAGEMENT_PORT_PROPERTY + "=" + assignedManagementPort);
    customPorts.put(MANAGEMENT_PORT, assignedManagementPort);
    return customPorts;
  }

  private void configureSpringProfiles() {
    addArguments(JVM_ARGUMENTS, "-D" + SPRING_PROFILES_INCLUDE_PROPERTY + "=dev");
  }

  private void addArguments(String property, String argument) {
    String previous = propertiesToOverwrite.get(property);
    if (previous != null) {
      argument = previous + " " + argument;
    }

    propertiesToOverwrite.put(property, argument);
  }
}
