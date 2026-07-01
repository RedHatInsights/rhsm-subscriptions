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
package com.redhat.swatch.component.tests.resources.artemis;

import static com.redhat.swatch.component.tests.utils.Ports.ARTEMIS_PORT;

import com.redhat.swatch.component.tests.resources.containers.OpenShiftContainerManagedResource;
import com.redhat.swatch.component.tests.utils.Ports;
import java.util.Map;

public class OpenShiftArtemisContainerManagedResource extends OpenShiftContainerManagedResource
    implements ArtemisEnvironmentResource {

  private static final String NAME = "artemis";
  private static final String SERVICE_NAME = "artemis-amqp-service";
  private static final String CONTAINER_NAME = "artemis-service";
  private static final String ARTEMIS_SERVICE_ACCOUNT = "nonprod-insightsrhsm-ephemeral";
  private static final Map<String, String> POD_LABELS = Map.of("app", NAME);

  private String namespace;

  public OpenShiftArtemisContainerManagedResource() {
    super(NAME, Map.of(Ports.DEFAULT_HTTP_PORT, ARTEMIS_PORT));
  }

  @Override
  public void start() {
    super.start();
    namespace = getOpenShiftClient().namespace();
  }

  @Override
  protected String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  protected String containerName() {
    return CONTAINER_NAME;
  }

  @Override
  protected Map<String, String> podLabels() {
    return POD_LABELS;
  }

  @Override
  public String normalizeChannel(String channel) {
    if (!channel.startsWith("VirtualTopic")) {
      return channel;
    }

    // UMB convention: Consumer.$service_account_name.$subscription_id.$topic
    String subscriptionId =
        String.format("swatch-%s-%s", this.namespace, channel.replace('.', '_'));

    return String.format("Consumer.%s.%s.%s", ARTEMIS_SERVICE_ACCOUNT, subscriptionId, channel);
  }
}
