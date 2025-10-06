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
package com.redhat.swatch.utilization.service;

import static com.redhat.swatch.utilization.configuration.Channels.UTILIZATION;
import static com.redhat.swatch.utilization.resources.InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR;
import static com.redhat.swatch.utilization.service.UtilizationSummaryConsumer.RECEIVED_METRIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.utilization.resources.InMemoryMessageBrokerKafkaResource;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
public class UtilizationSummaryConsumerTest {
  @Inject
  @Connector(IN_MEMORY_CONNECTOR)
  InMemoryConnector connector;

  @Inject MeterRegistry meterRegistry;

  InMemorySource<String> source;

  @BeforeEach
  void setup() {
    source = connector.source(UTILIZATION);
    meterRegistry.clear();
  }

  @Test
  void testMessageIncrementCounter() {
    whenSendUtilization("{utilization-message}");
    thenReceivedMetricIsIncremented();
  }

  private void whenSendUtilization(String message) {
    source.send(message);
  }

  private void thenReceivedMetricIsIncremented() {
    Awaitility.await()
        .untilAsserted(
            () -> {
              var metric = getReceivedMetric();
              assertTrue(metric.isPresent());
              assertEquals(1, metric.get().measure().iterator().next().getValue());
            });
  }

  private Optional<Meter> getReceivedMetric() {
    return meterRegistry.getMeters().stream()
        .filter(m -> RECEIVED_METRIC.equals(m.getId().getName()))
        .findFirst();
  }
}
