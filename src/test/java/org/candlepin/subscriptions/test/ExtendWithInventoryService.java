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
package org.candlepin.subscriptions.test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.candlepin.testcontainers.InventoryContainer;
import org.candlepin.testcontainers.SwatchPostgreSQLContainer;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
public interface ExtendWithInventoryService {

  Network INVENTORY_NETWORK = Network.newNetwork();

  @Container
  SwatchPostgreSQLContainer insightsDatabase =
      new SwatchPostgreSQLContainer("insights").withNetwork(INVENTORY_NETWORK);

  @Container
  InventoryContainer service =
      new InventoryContainer(insightsDatabase).withNetwork(INVENTORY_NETWORK);

  @DynamicPropertySource
  static void registerInventoryDbProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "INVENTORY_DATABASE_HOST",
        () -> insightsDatabase.getHost() + ":" + insightsDatabase.getFirstMappedPort());
  }

  default void deleteAllHostsFromInventoryDatabase() {
    insightsDatabase.deleteAllRows("hosts");
  }

  default void createHostInInventoryDatabase(
      UUID id,
      String orgId,
      String account,
      String displayName,
      Map<String, Object> facts,
      Map<String, Object> canonicalFacts,
      Map<String, Object> systemProfileFacts,
      String reporter,
      OffsetDateTime staleTimestamp,
      OffsetDateTime createdOn,
      OffsetDateTime modifiedOn) {
    ObjectMapper objectMapper = new ObjectMapper();

    try {
      insightsDatabase.insertRow(
          "hosts",
          new String[] {
            "id",
            "org_id",
            "account",
            "display_name",
            "facts",
            "canonical_facts",
            "system_profile_facts",
            "reporter",
            "groups",
            "stale_timestamp",
            "created_on",
            "modified_on"
          },
          new String[] {
            id.toString(),
            orgId,
            account,
            displayName,
            objectMapper.writeValueAsString(facts),
            objectMapper.writeValueAsString(canonicalFacts),
            objectMapper.writeValueAsString(systemProfileFacts),
            reporter,
            "{}",
            staleTimestamp.toString(),
            createdOn.toString(),
            modifiedOn.toString()
          });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to create `rhsm` json", e);
    }
  }
}
