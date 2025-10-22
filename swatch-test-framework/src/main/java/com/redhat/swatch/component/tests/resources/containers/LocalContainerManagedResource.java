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
import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.exceptions.ServiceNotFoundException;
import com.redhat.swatch.component.tests.logging.ContainerLoggingHandler;
import com.redhat.swatch.component.tests.logging.LoggingHandler;
import java.util.regex.Pattern;
import org.testcontainers.DockerClientFactory;

public class LocalContainerManagedResource extends ManagedResource {

  private final String containerRegex;
  private Container container;
  private LoggingHandler loggingHandler;

  public LocalContainerManagedResource(String containerNameRegEx) {
    this.containerRegex = containerNameRegEx;
  }

  @Override
  public void start() {
    if (isRunning()) {
      return;
    }

    container = findContainerByName(context.getOwner(), containerRegex);
    loggingHandler = new ContainerLoggingHandler(context, container);
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
  private static Container findContainerByName(Service service, String containerRegex) {
    var containers = DockerClientFactory.instance().client().listContainersCmd().exec();
    // Use find because the name actually starts with a slash
    var matches =
        containers.stream()
            .filter(x -> Pattern.compile(containerRegex).matcher(x.getNames()[0]).find())
            .toList();

    if (matches.size() > 1) {
      throw new ServiceNotFoundException(
          service.getName(), "Multiple matches found for " + containerRegex);
    }

    if (matches.isEmpty()) {
      throw new ServiceNotFoundException(service.getName(), "No match found for " + containerRegex);
    }

    return matches.get(0);
  }
}
