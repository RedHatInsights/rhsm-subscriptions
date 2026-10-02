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
package org.candlepin.subscriptions.clowder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ClowderDependencyEndpointsTest {
  private static final String RBAC = "clowder.dependency-endpoints.rbac.service.";
  private static final String EXPORT =
      "clowder.private-dependency-endpoints.export-service.service.";
  private static final String LEGACY =
      """
      "endpoints":[{"app":"rbac","name":"service","hostname":"old-rbac","port":8000,"tlsPort":8443}],
      "privateEndpoints":[{"app":"export-service","name":"service","hostname":"old-export","port":10000}],
      "tlsCAPath":"src/test/resources/ca-bundle.pem"
      """;

  @Test
  void selectsPublicAndPrivateIndependentlyWithoutGlobalCaLeakage() throws Exception {
    var source =
        source(
            """
        {"dependencyEndpoints":{"v2":{"rbac":{"service":{"uri":"https://new-rbac","authenticated":true}}}},
        "privateDependencyEndpoints":{"v2":{"export-service":{"service":{"uri":"https://new-export","authenticated":false}}}},
        "tlsCAPath":"/does-not-exist"}
        """);
    assertEquals("https://new-rbac", source.getProperty(RBAC + "uri"));
    assertEquals("true", source.getProperty(RBAC + "authenticated"));
    assertEquals("https://new-export", source.getProperty(EXPORT + "uri"));
    assertEquals("false", source.getProperty(EXPORT + "authenticated"));
    for (String prefix : new String[] {RBAC, EXPORT}) {
      assertNull(source.getProperty(prefix + "trust-store-path"));
      assertNull(source.getProperty(prefix + "trust-store-password"));
      assertNull(source.getProperty(prefix + "trust-store-type"));
    }
  }

  @Test
  void missingV2FallsBackToLegacyAndPreservesTls() throws Exception {
    var source = source("{" + LEGACY + "}");
    assertEquals("https://old-rbac:8443", source.getProperty(RBAC + "uri"));
    assertEquals("false", source.getProperty(RBAC + "authenticated"));
    assertTrue(source.getProperty(RBAC + "trust-store-path").toString().startsWith("file:"));
    assertEquals("PKCS12", source.getProperty(RBAC + "trust-store-type"));
    assertEquals("http://old-export:10000", source.getProperty(EXPORT + "uri"));
    assertNull(source.getProperty(EXPORT + "trust-store-path"));
  }

  @Test
  void blankV2UriFallsBackWithoutTakingItsAuthFlag() throws Exception {
    var source =
        source(
            "{\"dependencyEndpoints\":{\"v2\":{\"rbac\":{\"service\":{\"uri\":\" \",\"authenticated\":true}}}},"
                + LEGACY
                + "}");
    assertEquals("https://old-rbac:8443", source.getProperty(RBAC + "uri"));
    assertEquals("false", source.getProperty(RBAC + "authenticated"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"uri\":\"bad-uri\",\"authenticated\":true",
        "\"uri\":\"https://rbac\"",
        "\"uri\":\"https://rbac\",\"authenticated\":\"true\"",
        "\"uri\":\"https://user:secret@rbac\",\"authenticated\":true"
      })
  void populatedMalformedV2DoesNotDowngrade(String entry) throws Exception {
    var source =
        source(
            "{\"dependencyEndpoints\":{\"v2\":{\"rbac\":{\"service\":{"
                + entry
                + "}}}},"
                + LEGACY
                + "}");
    assertThrows(IllegalArgumentException.class, () -> source.getProperty(RBAC + "uri"));
  }

  @Test
  void cachesEachCaSeparatelyAndImportsBundle() throws Exception {
    var source =
        source(
            """
        {"dependencyEndpoints":{"v2":{"rbac":{"service":{"uri":"https://rbac","authenticated":true,"ca_certificate":"src/test/resources/ca-bundle.pem"}}}},
        "privateDependencyEndpoints":{"v2":{"export-service":{"service":{"uri":"https://export","authenticated":true,"ca_certificate":"src/test/resources/ca-test.pem"}}}}}
        """);
    String path = source.getProperty(RBAC + "trust-store-path").toString();
    assertEquals(path, source.getProperty(RBAC + "trust-store-path"));
    assertNotEquals(path, source.getProperty(EXPORT + "trust-store-path"));
    var store = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(Path.of(URI.create(path)))) {
      store.load(input, source.getProperty(RBAC + "trust-store-password").toString().toCharArray());
    }
    assertTrue(store.size() > 1);
  }

  @Test
  void invalidEndpointCaFailsInsteadOfUsingSystemTrust() throws Exception {
    var source =
        source(
            """
        {"dependencyEndpoints":{"v2":{"rbac":{"service":{"uri":"https://rbac","authenticated":true,"ca_certificate":"/does-not-exist"}}}}}
        """);
    assertThrows(IllegalStateException.class, () -> source.getProperty(RBAC + "trust-store-path"));
  }

  @Test
  void absentOptionalEndpointAndExplicitOverridesResolveNormally() throws Exception {
    var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(source("{}"));
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "defaults",
                Map.of(
                    "RHSM_RBAC_URL", "${" + RBAC + "uri:http://localhost:8819}",
                    "EXPORT_SERVICE_AUTHENTICATED", "${" + EXPORT + "authenticated:false}")));
    assertEquals("http://localhost:8819", environment.getProperty("RHSM_RBAC_URL"));
    assertEquals("false", environment.getProperty("EXPORT_SERVICE_AUTHENTICATED"));
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("overrides", Map.of("RHSM_RBAC_URL", "https://override")));
    assertEquals("https://override", environment.getProperty("RHSM_RBAC_URL"));
  }

  private static ClowderJsonPropertySource source(String json) throws Exception {
    return new ClowderJsonPropertySource(
        new ClowderJson(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), new ObjectMapper()));
  }
}
