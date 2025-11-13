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

public abstract class OpenShiftContainerManagedResource extends ManagedResource {

  private static final String POD_LABEL = "pod";

  private final String serviceName;
  private final Map<Integer, Integer> portsMapping;
  private OpenshiftClient client;
  private LoggingHandler loggingHandler;
  private boolean running;

  public OpenShiftContainerManagedResource(String serviceName, Map<Integer, Integer> portsMapping) {
    this.serviceName = serviceName;
    this.portsMapping = portsMapping;
  }

  @Override
  public String getDisplayName() {
    return serviceName;
  }

  @Override
  public void start() {
    if (running) {
      return;
    }

    client = context.get(OpenShiftExtensionBootstrap.CLIENT);
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
        serviceName(), portsMapping.getOrDefault(port, port), context.getOwner(), podLabels());
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

  protected OpenshiftClient getOpenShiftClient() {
    if (client == null) {
      throw new IllegalStateException("OpenShift client is not initialized yet!");
    }

    return client;
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

  protected String serviceName() {
    return serviceName;
  }

  protected String containerName() {
    return serviceName();
  }

  protected String getExpectedLog() {
    return EMPTY;
  }

  private void validateService() {
    // check whether the service does exist
    client.checkServiceExists(serviceName());
    // check whether pods do exist
    client.checkPodsExists(serviceName(), podLabels(), containerName());
  }
}
