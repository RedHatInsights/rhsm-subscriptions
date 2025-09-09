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

import com.redhat.swatch.component.tests.api.clients.OpenshiftClient;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import java.util.Map;
import java.util.Map.Entry;

public class OpenShiftLoggingHandler extends ServiceLoggingHandler {

  private final OpenshiftClient client;
  private final Map<String, String> podLabels;
  private final String containerName;

  private Map<String, String> oldLogs;

  public OpenShiftLoggingHandler(
      Map<String, String> podLabels, String containerName, ServiceContext context) {
    super(context.getOwner());

    this.podLabels = podLabels;
    this.containerName = containerName;
    this.client = context.get(OpenShiftExtensionBootstrap.CLIENT);
  }

  @Override
  protected synchronized void handle() {
    Map<String, String> newLogs = client.logs(podLabels, containerName);
    for (Entry<String, String> entry : newLogs.entrySet()) {
      onMapDifference(entry);
    }

    oldLogs = newLogs;
  }

  private void onMapDifference(Entry<String, String> entry) {
    String newPodLogs = formatPodLogs(entry.getKey(), entry.getValue());

    if (oldLogs != null && oldLogs.containsKey(entry.getKey())) {
      String oldPodLogs = formatPodLogs(entry.getKey(), oldLogs.get(entry.getKey()));

      onStringDifference(newPodLogs, oldPodLogs);
    } else {
      onLines(newPodLogs);
    }
  }

  private String formatPodLogs(String podName, String log) {
    return String.format("[%s] %s", podName, log);
  }
}
