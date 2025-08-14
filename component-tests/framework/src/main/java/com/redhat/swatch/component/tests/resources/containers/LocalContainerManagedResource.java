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

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.logging.ContainerLoggingHandler;
import com.redhat.swatch.component.tests.logging.LoggingHandler;
import org.testcontainers.DockerClientFactory;

public class LocalContainerManagedResource extends ManagedResource {

  private final String containerName;
  private Container container;
  private LoggingHandler loggingHandler;

  public LocalContainerManagedResource(String containerName) {
    this.containerName = containerName;
  }

  @Override
  public void start() {
    if (isRunning()) {
      return;
    }

    container = findContainerByName(containerName);
    loggingHandler = new ContainerLoggingHandler(context.getOwner(), container);
    loggingHandler.startWatching();
  }

  @Override
  public void stop() {
    if (loggingHandler != null) {
      loggingHandler.stopWatching();
    }

    if (isRunning()) {
      container = null;
    }
  }

  @Override
  public String getHost() {
    return "localhost";
  }

  @Override
  public int getMappedPort(int port) {
    for (ContainerPort containerPort : container.getPorts()) {
      if (containerPort.getPrivatePort() != null && containerPort.getPrivatePort() == port) {
        return containerPort.getPublicPort();
      }
    }
    throw new RuntimeException("Port not mapped: " + port);
  }

  @Override
  public boolean isRunning() {
    return container != null;
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  @SuppressWarnings("resource")
  private static Container findContainerByName(String containerName) {
    for (Container c : DockerClientFactory.instance().client().listContainersCmd().exec()) {
      if (c.getNames()[0].contains(containerName)) {
        return c;
      }
    }

    throw new RuntimeException("Container not found: " + containerName);
  }
}
