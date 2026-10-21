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
package com.redhat.swatch.clowder;

import static com.redhat.swatch.clowder.ClowderConfigInfoContributor.SECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClowderConfigInfoContributorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Fixture follows Clowder Dependencies Provider docs: V1 {@code endpoints} / {@code
   * privateEndpoints} and V2 {@code dependencyEndpoints} / {@code privateDependencyEndpoints}.
   *
   * @see <a
   *     href="https://github.com/RedHatInsights/clowder/blob/master/docs/providers/dependencies.md">Clowder
   *     Dependencies Provider</a>
   */
  private static final String CLOWDER_DOC_SHAPED_CONFIG =
      """
      {
        "database": {
          "password": "secret-db-password",
          "username": "secret-db-user"
        },
        "endpoints": [
          {
            "name": "deployment1",
            "app": "app_name1",
            "hostname": "deployment1.svc",
            "port": 8000
          },
          {
            "name": "deployment2",
            "app": "app_name2",
            "hostname": "deployment2.svc",
            "port": 8000
          }
        ],
        "privateEndpoints": [
          {
            "name": "deployment1",
            "app": "app_name1",
            "hostname": "deployment1.svc",
            "port": 10000
          }
        ],
        "dependencyEndpoints": {
          "v2": {
            "app_name1": {
              "deployment1": {
                "uri": "http://app_name1-deployment1.namespace.svc:8000",
                "authenticated": false
              }
            },
            "app_name2": {
              "deployment1": {
                "uri": "https://app_name2-deployment1.namespace.svc:8443",
                "authenticated": false,
                "ca_certificate": "/cdapp/certs/service-ca.crt"
              }
            }
          }
        },
        "privateDependencyEndpoints": {
          "v2": {
            "app_name1": {
              "deployment1": {
                "uri": "http://app_name1-deployment1.namespace.svc:10000",
                "authenticated": false
              }
            }
          }
        }
      }
      """;

  @TempDir Path tempDir;

  @Test
  void exposesV1AndV2ConfigWithoutSecrets() throws Exception {
    Path config = Files.writeString(tempDir.resolve("cdappconfig.json"), CLOWDER_DOC_SHAPED_CONFIG);

    ClowderConfigInfoContributor contributor =
        new ClowderConfigInfoContributor(MAPPER, config.toString());

    assertEquals(SECTION, contributor.name());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) contributor.data();

    assertEquals(4, data.size(), "only V1/V2 endpoint slices; other cdappconfig keys stay out");
    assertFalse(data.containsKey("database"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> endpoints = (List<Map<String, Object>>) data.get("endpoints");
    assertEquals(2, endpoints.size());
    assertEquals("app_name1", endpoints.get(0).get("app"));
    assertEquals("deployment1", endpoints.get(0).get("name"));
    assertEquals("deployment1.svc", endpoints.get(0).get("hostname"));
    assertEquals(8000, endpoints.get(0).get("port"));
    assertEquals("app_name2", endpoints.get(1).get("app"));
    assertEquals("deployment2", endpoints.get(1).get("name"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> privateEndpoints =
        (List<Map<String, Object>>) data.get("privateEndpoints");
    assertEquals(1, privateEndpoints.size());
    assertEquals("app_name1", privateEndpoints.get(0).get("app"));
    assertEquals("deployment1", privateEndpoints.get(0).get("name"));
    assertEquals(10000, privateEndpoints.get(0).get("port"));

    @SuppressWarnings("unchecked")
    Map<String, Object> publicData = (Map<String, Object>) data.get("dependencyEndpoints");
    @SuppressWarnings("unchecked")
    Map<String, Object> publicV2 = (Map<String, Object>) publicData.get("v2");
    @SuppressWarnings("unchecked")
    Map<String, Object> app1Public =
        (Map<String, Object>) ((Map<String, Object>) publicV2.get("app_name1")).get("deployment1");
    @SuppressWarnings("unchecked")
    Map<String, Object> app2Public =
        (Map<String, Object>) ((Map<String, Object>) publicV2.get("app_name2")).get("deployment1");

    assertEquals("http://app_name1-deployment1.namespace.svc:8000", app1Public.get("uri"));
    assertEquals(false, app1Public.get("authenticated"));
    assertEquals("https://app_name2-deployment1.namespace.svc:8443", app2Public.get("uri"));
    assertEquals(false, app2Public.get("authenticated"));
    assertFalse(app2Public.containsKey("ca_certificate"));

    @SuppressWarnings("unchecked")
    Map<String, Object> privateData = (Map<String, Object>) data.get("privateDependencyEndpoints");
    @SuppressWarnings("unchecked")
    Map<String, Object> privateV2 = (Map<String, Object>) privateData.get("v2");
    @SuppressWarnings("unchecked")
    Map<String, Object> app1Private =
        (Map<String, Object>) ((Map<String, Object>) privateV2.get("app_name1")).get("deployment1");
    assertEquals("http://app_name1-deployment1.namespace.svc:10000", app1Private.get("uri"));
    assertEquals(false, app1Private.get("authenticated"));
  }

  @Test
  void returnsEmptyWhenConfigMissing() {
    assertTrue(ClowderCdappConfig.read(tempDir.resolve("missing.json"), MAPPER).isEmpty());
  }
}
