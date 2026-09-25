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
package com.redhat.swatch.contract.service.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.cloud.common.clowder.configsource.ClowderConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Resolve SWATCH's actual application properties through the POC library, not copied mappings. */
class HccEndpointConfigurationTest {
  private static final String ACCESS =
      "quarkus.rest-client.\"com.redhat.swatch.clients.rbac.api.resources.AccessApi\".";
  private static final String EXPORT =
      "quarkus.rest-client.\"com.redhat.swatch.clients.export.api.resources.ExportApi\".";
  private static final String V2 =
      """
      {
        "tlsCAPath": "/an-unrelated-global-ca-that-must-not-be-read.pem",
        "endpoints": [{"app":"rbac","name":"service","hostname":"old-rbac","tlsPort":8443}],
        "privateEndpoints": [{"app":"export-service","name":"service","hostname":"old-export","port":10000}],
        "dependencyEndpoints":{"v2":{"rbac":{"service":{"uri":"https://rbac.example:443","authenticated":true}}}},
        "privateDependencyEndpoints":{"v2":{"export-service":{"service":{"uri":"https://export.example:443","authenticated":true}}}}
      }
      """;
  @TempDir Path temp;

  @Test
  void v2MapsUrlsAuthenticationAndSystemTrustForBothClients() throws Exception {
    SmallRyeConfig config = config(V2, Map.of(), null);
    assertEquals(
        "https://rbac.example:443/api/rbac/v1", config.getValue(ACCESS + "url", String.class));
    assertEquals(
        "https://rbac.example:443",
        config.getValue("swatch.kessel.rbac-base-endpoint", String.class));
    assertEquals("https://export.example:443", config.getValue(EXPORT + "url", String.class));
    assertTrue(config.getValue("RBAC_AUTHENTICATED", Boolean.class));
    assertTrue(config.getValue("EXPORT_SERVICE_AUTHENTICATED", Boolean.class));
    assertFalse(config.getOptionalValue(ACCESS + "trust-store", String.class).isPresent());
    assertFalse(config.getOptionalValue(EXPORT + "trust-store", String.class).isPresent());
    assertEquals(
        "com.redhat.swatch.common.security.RbacAuthHeaderProvider",
        config.getValue(ACCESS + "providers", String.class));
    assertEquals(
        ExportPskHeaderProvider.class.getName(),
        config.getValue(EXPORT + "providers", String.class));
  }

  @Test
  void v1FallbackKeepsLegacyUrlsAndAuthentication() throws Exception {
    SmallRyeConfig config =
        config(
            """
        {"endpoints":[{"app":"rbac","name":"service","hostname":"rbac.svc","port":8000}],
         "privateEndpoints":[{"app":"export-service","name":"service","hostname":"export.svc","port":10000}]}
        """,
            Map.of(),
            null);
    assertEquals("http://rbac.svc:8000/api/rbac/v1", config.getValue(ACCESS + "url", String.class));
    assertEquals("http://export.svc:10000", config.getValue(EXPORT + "url", String.class));
    assertFalse(config.getValue("RBAC_AUTHENTICATED", Boolean.class));
    assertFalse(config.getValue("EXPORT_SERVICE_AUTHENTICATED", Boolean.class));
  }

  @Test
  void missingOptionalExportSupportsBooleanDefaultsWithoutPsk() throws Exception {
    SmallRyeConfig config = config("{}", Map.of(), null);
    assertFalse(config.getValue("EXPORT_SERVICE_AUTHENTICATED", Boolean.class));
    assertFalse(config.getOptionalValue("EXPORT_SERVICE_ENDPOINT", String.class).isPresent());
    assertFalse(config.getOptionalValue("SWATCH_EXPORT_PSK", String.class).isPresent());
  }

  @Test
  void explicitEndpointAndAuthOverridesArePreserved() throws Exception {
    SmallRyeConfig config =
        config(
            V2,
            Map.of(
                "RBAC_ENDPOINT",
                "http://localhost:8888",
                "RBAC_AUTHENTICATED",
                "false",
                "EXPORT_SERVICE_ENDPOINT",
                "http://localhost:9999",
                "EXPORT_SERVICE_AUTHENTICATED",
                "false"),
            null);
    assertEquals(
        "http://localhost:8888/api/rbac/v1", config.getValue(ACCESS + "url", String.class));
    assertEquals("http://localhost:9999", config.getValue(EXPORT + "url", String.class));
    assertFalse(config.getValue("RBAC_AUTHENTICATED", Boolean.class));
    assertFalse(config.getValue("EXPORT_SERVICE_AUTHENTICATED", Boolean.class));
  }

  @Test
  void testProfileKeepsLocalEndpointsAndLegacyAuthentication() throws Exception {
    SmallRyeConfig config = config(V2, Map.of(), "test");
    assertEquals(
        "http://localhost:8080/api/rbac/v1", config.getValue(ACCESS + "url", String.class));
    assertEquals("http://localhost:10000", config.getValue(EXPORT + "url", String.class));
    assertFalse(config.getValue("RBAC_AUTHENTICATED", Boolean.class));
    assertFalse(config.getValue("EXPORT_SERVICE_AUTHENTICATED", Boolean.class));
    assertEquals("placeholder", config.getValue("SWATCH_EXPORT_PSK", String.class));
  }

  private SmallRyeConfig config(String json, Map<String, String> overrides, String profile)
      throws Exception {
    Path file = temp.resolve("cdappconfig.json");
    Files.writeString(file, json);
    var builder =
        new SmallRyeConfigBuilder()
            .addDefaultInterceptors()
            .withSources(
                new PropertiesConfigSource(
                    Path.of("../swatch-common-security/src/main/resources/application.properties")
                        .toUri()
                        .toURL(),
                    250))
            .withSources(
                new PropertiesConfigSource(
                    Path.of("src/main/resources/application.properties").toUri().toURL(), 250))
            .withSources(
                new PropertiesConfigSource(
                    Map.of("acg.config", file.toString()), "clowder file", 350))
            .withSources(new PropertiesConfigSource(overrides, "explicit overrides", 400))
            .withSources(new ClowderConfigSourceFactory());
    if (profile != null) {
      builder.withProfile(profile);
    }
    return builder.build();
  }
}
