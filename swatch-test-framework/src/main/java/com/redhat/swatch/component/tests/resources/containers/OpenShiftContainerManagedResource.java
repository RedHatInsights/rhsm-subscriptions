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
package com.redhat.swatch.component.tests.resources.containers;

import static com.redhat.swatch.component.tests.utils.StringUtils.EMPTY;

import com.redhat.swatch.component.tests.api.clients.OpenshiftClient;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftServiceConfiguration;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftServiceConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import com.redhat.swatch.component.tests.logging.LoggingHandler;
import com.redhat.swatch.component.tests.logging.OpenShiftLoggingHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class OpenShiftContainerManagedResource extends ManagedResource {

  private static final String POD_LABEL = "pod";

  private String serviceName;
  private String displayName;
  private final Supplier<String> serviceNameSupplier;
  private final Map<Integer, Integer> portsMapping;
  private OpenshiftClient client;
  private LoggingHandler loggingHandler;
  private boolean running;

  protected OpenShiftContainerManagedResource(
      String serviceName, Map<Integer, Integer> portsMapping) {
    this.serviceNameSupplier = () -> serviceName;
    this.displayName = serviceName;
    this.portsMapping = portsMapping;
  }

  /**
   * Constructor for subclasses that need to compute service name dynamically after client
   * initialization. Subclasses should override {@link #buildServiceName()} to provide the service
   * name.
   */
  protected OpenShiftContainerManagedResource(Map<Integer, Integer> portsMapping) {
    this.serviceNameSupplier = this::buildServiceName;
    this.displayName = "<not yet resolved>";
    this.portsMapping = portsMapping;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public void start() {
    if (running) {
      return;
    }

    client = context.get(OpenShiftExtensionBootstrap.CLIENT);

    // Ensure serviceName is evaluated from supplier before use
    if (Objects.isNull(serviceName)) {
      serviceName = serviceNameSupplier.get();
      displayName = serviceName;
    }

    validateService();
    loggingHandler = new OpenShiftLoggingHandler(podLabels(), containerName(), context);
    loggingHandler.startWatching();
    running = true;
  }

  @Override
  public void stop() {
    if (loggingHandler != null) {
      loggingHandler.stopWatching();
    }

    running = false;
  }

  @Override
  public String getHost() {
    return "localhost";
  }

  @Override
  public int getMappedPort(int port) {
    return client.port(
        serviceName, portsMapping.getOrDefault(port, port), context.getOwner(), podLabels());
  }

  @Override
  public boolean isRunning() {
    return loggingHandler != null && loggingHandler.logsContains(getExpectedLog());
  }

  @Override
  public List<String> logs() {
    return loggingHandler.logs();
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        OpenShiftServiceConfiguration.class, new OpenShiftServiceConfigurationBuilder());
  }

  protected Map<String, String> podLabels() {
    return Map.of(POD_LABEL, serviceName);
  }

  protected String containerName() {
    return serviceName;
  }

  protected String namespace() {
    return client.namespace();
  }

  protected String getExpectedLog() {
    return EMPTY;
  }

  /**
   * This method is called after the client is initialized in {@link #start()}, so subclasses can
   * safely use {@link #namespace()} and other client-dependent methods.
   *
   * @return the service name
   */
  protected String buildServiceName() {
    throw new UnsupportedOperationException("Method must be implemented by child classes");
  }

  private void validateService() {
    // check whether the service does exist
    client.checkServiceExists(serviceName);
    // check whether pods do exist
    client.checkPodsExists(serviceName, podLabels(), containerName());
  }
}
