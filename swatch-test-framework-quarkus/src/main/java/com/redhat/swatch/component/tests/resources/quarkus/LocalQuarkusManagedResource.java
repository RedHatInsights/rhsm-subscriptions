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
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.resources.process.DevProcessManagedResource;
import com.redhat.swatch.component.tests.utils.SocketUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LocalQuarkusManagedResource extends DevProcessManagedResource {

  private static final List<String> ERRORS =
      Arrays.asList(
          "Failed to start application",
          "Failed to load config value of type class",
          "Quarkus may already be running or the port is used by another application",
          "One or more configuration errors have prevented the application from starting",
          "Attempting to start live reload endpoint to recover from previous Quarkus startup failure",
          "Dev mode process did not complete successfully",
          "Failed to run",
          "BUILD FAILURE");

  private static final String MANAGEMENT_PORT_PROPERTY = "quarkus.management.port";

  protected Map<String, String> propertiesToOverwrite = new HashMap<>();

  private int assignedHttpPort;
  private Integer assignedDebugPort;
  private Map<Integer, Integer> assignedCustomPorts;

  public LocalQuarkusManagedResource(String service) {
    super(service);
  }

  @Override
  protected String getDevModeCommand() {
    return "quarkus:dev";
  }

  @Override
  public int getMappedPort(int port) {
    return Optional.ofNullable(assignedCustomPorts.get(port)).orElse(assignedHttpPort);
  }

  public String getExpectedLog() {
    return context.getConfigurationAs(QuarkusServiceConfiguration.class).getExpectedLog();
  }

  @Override
  protected List<String> getExpectedErrorLogs() {
    return ERRORS;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        QuarkusServiceConfiguration.class, new QuarkusServiceConfigurationBuilder());
    assignPorts();
  }

  @Override
  protected void configureCommand(List<String> command) {
    Map<String, String> runtimeProperties = new HashMap<>(context.getOwner().getProperties());
    runtimeProperties.putAll(propertiesToOverwrite);
    runtimeProperties.entrySet().stream()
        .map(e -> "-D" + e.getKey() + "=" + e.getValue())
        .forEach(command::add);

    if (context.isDebug()) {
      assignedDebugPort = SocketUtils.findAvailablePort(context.getOwner());
      command.add("-Ddebug=" + assignedDebugPort);
      command.add("-Dsuspend");
    }
  }

  private void assignPorts() {
    assignedHttpPort = getOrAssignPortByProperty(SERVER_PORT_PROPERTY);
    propertiesToOverwrite.put(SERVER_PORT_PROPERTY, "" + assignedHttpPort);
    this.assignedCustomPorts = assignCustomPorts();
  }

  protected Map<Integer, Integer> assignCustomPorts() {
    Map<Integer, Integer> customPorts = new HashMap<>();
    int assignedManagementPort = getOrAssignPortByProperty(MANAGEMENT_PORT_PROPERTY);
    propertiesToOverwrite.put(MANAGEMENT_PORT_PROPERTY, "" + assignedManagementPort);
    customPorts.put(MANAGEMENT_PORT, assignedManagementPort);
    return customPorts;
  }
}
