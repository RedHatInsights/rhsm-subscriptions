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
package com.redhat.swatch;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class KafkaResource implements QuarkusTestResourceLifecycleManager {

  static KafkaContainer kafka =
      new CustomKafkaContainer(
          DockerImageName.parse("quay.io/cloudservices/cp-kafka")
              .asCompatibleSubstituteFor("confluentinc/cp-kafka"));

  @Override
  public Map<String, String> start() {
    kafka.start();
    return Collections.singletonMap("kafka.bootstrap.servers", kafka.getBootstrapServers());
  }

  @Override
  public void stop() {
    kafka.stop();
  }

  /*
   * Hack to override the STARTER_SCRIPT location, so that this can run in OpenShift, where
   * the root filesystem is read-only.
   */
  static class CustomKafkaContainer extends KafkaContainer {
    private static final String STARTER_SCRIPT = "/tmp/testcontainers_start.sh";

    public CustomKafkaContainer(final DockerImageName dockerImageName) {
      super(dockerImageName);
      withCommand(
          "-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
    }

    @Override
    public void copyFileToContainer(Transferable transferable, String containerPath) {
      super.copyFileToContainer(transferable, "/tmp" + containerPath);
    }
  }
}
