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
package org.candlepin.subscriptions.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.ClientInfo;
import com.redhat.swatch.clients.ClientsInfoContributor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "kafka-queue", "test"})
class DependenciesInfoContributorTest {

  @Autowired DependenciesInfoContributor contributor;
  @Autowired List<ClientInfo> clients;

  @Test
  void wiresExportServiceAsClientInfoAndContributesDependenciesInfo() {
    assertFalse(clients.isEmpty());
    assertTrue(clients.stream().anyMatch(client -> "export-service".equals(client.name())));

    Info.Builder builder = new Info.Builder();
    contributor.contribute(builder);

    @SuppressWarnings("unchecked")
    Map<String, Object> section =
        (Map<String, Object>) builder.build().getDetails().get(ClientsInfoContributor.INFO_SECTION);
    assertTrue(section.containsKey("export-service"));

    @SuppressWarnings("unchecked")
    Map<String, Object> exportInfo = (Map<String, Object>) section.get("export-service");
    assertEquals("v1", exportInfo.get("current"));
    assertFalse(exportInfo.containsKey("trustStorePassword"));

    @SuppressWarnings("unchecked")
    Map<String, Object> primary = (Map<String, Object>) exportInfo.get("primary");
    assertEquals("http://localhost:10000", primary.get("uri"));
  }
}
