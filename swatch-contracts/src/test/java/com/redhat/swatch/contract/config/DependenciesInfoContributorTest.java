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
package com.redhat.swatch.contract.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.ClientInfo;
import com.redhat.swatch.clients.ClientsInfoContributor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DependenciesInfoContributorTest {

  @Inject DependenciesInfoContributor contributor;
  @Inject Instance<ClientInfo> clients;

  @ConfigProperty(name = "EXPORT_SERVICE_ENDPOINT")
  String exportServiceEndpoint;

  @Test
  void wiresExportServiceAsClientInfoAndContributesDependenciesInfo() {
    assertFalse(StreamSupport.stream(clients.spliterator(), false).toList().isEmpty());
    assertTrue(
        StreamSupport.stream(clients.spliterator(), false)
            .anyMatch(client -> "export-service".equals(client.name())));

    assertEquals(ClientsInfoContributor.INFO_SECTION, contributor.name());

    @SuppressWarnings("unchecked")
    Map<String, Object> section = (Map<String, Object>) contributor.data();
    assertTrue(section.containsKey("export-service"));

    @SuppressWarnings("unchecked")
    Map<String, Object> exportInfo = (Map<String, Object>) section.get("export-service");
    assertEquals("v1", exportInfo.get("current"));
    assertFalse(exportInfo.containsKey("trustStorePassword"));

    @SuppressWarnings("unchecked")
    Map<String, Object> primary = (Map<String, Object>) exportInfo.get("primary");
    assertEquals(exportServiceEndpoint, primary.get("uri"));
  }
}
