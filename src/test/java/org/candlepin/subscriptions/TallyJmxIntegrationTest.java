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
package org.candlepin.subscriptions;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@Slf4j
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${rhsm-subscriptions.tasks.topic}",
      "${rhsm-subscriptions.subscription.tasks.topic}"
    })
@Tag("integration")
@AutoConfigureMetrics
@ActiveProfiles({"worker", "kafka-queue", "test", "test-kafka"})
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.jmx.enabled=true",
      "rhsm-subscriptions.inventory-service.datasource.url=jdbc:tc:postgresql:latest:///inventory?TC_DAEMON=true",
      "rhsm-subscriptions.inventory-service.datasource.platform=postgresql",
      "rhsm-subscriptions.inventory-service.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    })
class TallyJmxIntegrationTest extends TallyJmxSmokeTest {
  @LocalServerPort Integer serverPort;

  @Autowired
  @Qualifier("inventoryDataSource")
  HikariDataSource inventoryDataSource;

  @Override
  protected int getServerPort() {
    return serverPort;
  }

  @BeforeEach
  void initDb() throws SQLException {
    try (Connection connection = inventoryDataSource.getConnection()) {
      Statement statement = connection.createStatement();
      statement.executeUpdate(
          "create table if not exists hosts\n"
              + "(\n"
              + "    id                   uuid                     not null\n"
              + "        constraint hosts_pkey\n"
              + "            primary key,\n"
              + "    account              varchar(10)              not null,\n"
              + "    display_name         varchar(200),\n"
              + "    created_on           timestamp with time zone not null,\n"
              + "    modified_on          timestamp with time zone not null,\n"
              + "    facts                jsonb,\n"
              + "    tags                 jsonb,\n"
              + "    canonical_facts      jsonb                    not null,\n"
              + "    system_profile_facts jsonb,\n"
              + "    ansible_host         varchar(255),\n"
              + "    stale_timestamp      timestamp with time zone not null,\n"
              + "    reporter             varchar(255)             not null\n"
              + ");");
    }
  }

  @Test
  void tallyJmxEnforcesAntiCsrf() {
    WebTestClient wrongOrigin = client.mutate().defaultHeader("Origin", "bad.example.com").build();
    invokeViaJmx(
            wrongOrigin,
            "org.candlepin.subscriptions.jmx:name=tallyJmxBean,type=TallyJmxBean",
            "tallyAccount(java.lang.String)",
            List.of("account123"))
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
