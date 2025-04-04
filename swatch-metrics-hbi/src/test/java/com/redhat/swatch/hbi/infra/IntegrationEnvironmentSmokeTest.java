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
package com.redhat.swatch.hbi.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.config.FeatureFlags;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
public class IntegrationEnvironmentSmokeTest {

  @Inject DataSource dataSource;

  @Inject FeatureFlags featureFlags;

  @Test
  void shouldConnectToPostgresContainer() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      assertTrue(conn.isValid(2));
    }
  }

  @Test
  void shouldStartInMemoryKafkaChannels() {
    assertTrue(InMemoryMessageBrokerKafkaResource.IN_MEMORY_CONNECTOR.equals("smallrye-in-memory"));
  }

  @Test
  void shouldHaveFakeUnleashEnabledForEmitEvents() {
    FakeUnleashProducer.getInstance().enable("swatch.swatch-metrics-hbi.emit-events");
    assertTrue(featureFlags.emitEvents(), "Feature flag 'emit-events' should be enabled");
  }
}
