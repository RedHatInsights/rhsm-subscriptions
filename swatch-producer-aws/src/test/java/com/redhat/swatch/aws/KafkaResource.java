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
package com.redhat.swatch.aws;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.github.embeddedkafka.EmbeddedK;
import io.github.embeddedkafka.EmbeddedKafka;
import io.github.embeddedkafka.EmbeddedKafkaConfig;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;

public class KafkaResource implements QuarkusTestResourceLifecycleManager {

  private EmbeddedK kafka;

  @Override
  public Map<String, String> start() {
    kafka = EmbeddedKafka.start(EmbeddedKafkaConfig.defaultConfig());
    await()
        .until(() -> kafka.broker().brokerState() == org.apache.kafka.metadata.BrokerState.RUNNING);
    return Collections.singletonMap(
        "kafka.bootstrap.servers", "PLAINTEXT://localhost:" + kafka.config().kafkaPort());
  }

  @Override
  public void stop() {
    try {
      if (kafka != null) {
        kafka.stop(true);
        kafka = null;
      }
    } catch (Exception ignored) {

    }
  }
}
