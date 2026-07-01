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
package com.redhat.swatch.component.tests.logging;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.redhat.swatch.component.tests.core.ServiceContext;
import org.testcontainers.DockerClientFactory;

public class ContainerLoggingHandler extends ServiceLoggingHandler {

  private final Container container;
  private String oldLogs;

  public ContainerLoggingHandler(ServiceContext service, Container container) {
    super(service);

    this.container = container;
  }

  @Override
  protected void handle() {
    String newLogs = getLogsFromContainer();
    onStringDifference(newLogs, oldLogs);
    oldLogs = newLogs;
  }

  @SuppressWarnings("resource")
  private String getLogsFromContainer() {
    StringBuilder logs = new StringBuilder();
    LogContainerCmd cmd =
        DockerClientFactory.instance()
            .client()
            .logContainerCmd(container.getId())
            .withStdOut(true)
            .withStdErr(true)
            .withTailAll();

    try {
      cmd.exec(
              new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                  logs.append(new String(frame.getPayload()));
                }
              })
          .awaitCompletion();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return logs.toString();
  }
}
