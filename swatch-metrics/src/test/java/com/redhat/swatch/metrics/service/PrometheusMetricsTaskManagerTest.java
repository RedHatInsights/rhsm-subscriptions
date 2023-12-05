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
package com.redhat.swatch.metrics.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.redhat.swatch.metrics.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class PrometheusMetricsTaskManagerTest {

  private static final String TEST_PROFILE_ID = "OpenShift-dedicated-metrics";

  @Inject PrometheusMetricsTaskManager manager;
  @Inject @Any InMemoryConnector connector;

  @Test
  void testTasksAreProducedToTopic() {
    InMemorySink<Object> results = connector.sink("tasks-out");

    String orgId = "org123";
    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(1);
    manager.updateMetricsForOrgId(orgId, TEST_PROFILE_ID, start, end);
    assertFalse(results.received().isEmpty());
  }
}
