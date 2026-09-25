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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** Verify the real Spring application files, including the endpoint and credential overrides. */
class ClowderSpringConfigurationTest {
  private static final String RBAC = "rhsm-subscriptions.rbac-service.";
  private static final String EXPORT = "rhsm-subscriptions.export-service.";
  private static final String KESSEL = "rhsm-subscriptions.kessel.";
  private static final String V2 =
      """
      {"dependencyEndpoints":{"v2":{"rbac":{"service":{"uri":"https://new-rbac","authenticated":true}}}},
       "privateDependencyEndpoints":{"v2":{"export-service":{"service":{"uri":"https://new-export","authenticated":true}}}},
       "endpoints":[{"app":"kessel","name":"inventory-api","hostname":"kessel.svc","port":8000}],
       "tlsCAPath":"/invalid-global-ca"}
      """;

  @Test
  void v2SettingsReachBothClientsAndWorkspaceLookup() throws Exception {
    var env =
        environment(V2, Map.of("KESSEL_INVENTORY_API_PORT", "443", "KESSEL_AUTH_ENABLED", "true"));
    assertEquals("https://new-rbac/api/rbac/v1", env.getProperty(RBAC + "url"));
    assertEquals("true", env.getProperty(RBAC + "authenticated"));
    assertEquals("https://new-rbac", env.getProperty(KESSEL + "rbac-base-endpoint"));
    assertEquals("https://new-export", env.getProperty(EXPORT + "url"));
    assertEquals("true", env.getProperty(EXPORT + "authenticated"));
    assertEquals("", env.getProperty(EXPORT + "psk"));
    assertEquals("", env.getProperty(RBAC + "truststore"));
    assertEquals("", env.getProperty(EXPORT + "truststore"));
    assertEquals("kessel.svc:443", env.getProperty(KESSEL + "endpoint"));
    assertEquals("true", env.getProperty(KESSEL + "auth-enabled"));
    var bound = Binder.get(env).bind("rhsm-subscriptions.rbac-service", RbacProperties.class).get();
    org.junit.jupiter.api.Assertions.assertNull(bound.getTruststore());
    assertTrue(bound.isAuthenticated());
  }

  @Test
  void v1AndLocalFallbackRemainIndependent() throws Exception {
    var env =
        environment(
            """
        {"endpoints":[{"app":"rbac","name":"service","hostname":"rbac.svc","port":8000}],
         "privateEndpoints":[{"app":"export-service","name":"service","hostname":"export.svc","port":10000}]}
        """,
            Map.of());
    assertEquals("http://rbac.svc:8000/api/rbac/v1", env.getProperty(RBAC + "url"));
    assertEquals("http://export.svc:10000", env.getProperty(EXPORT + "url"));
    assertEquals("false", env.getProperty(EXPORT + "authenticated"));
    assertEquals("false", env.getProperty(RBAC + "authenticated"));
    assertEquals("localhost:9000", env.getProperty(KESSEL + "endpoint"));
  }

  @Test
  void explicitOverridesWin() throws Exception {
    var env =
        environment(
            V2,
            Map.of(
                "RHSM_RBAC_URL", "http://local-rbac:8080",
                "RBAC_AUTHENTICATED", "false",
                "EXPORT_SERVICE_ENDPOINT", "http://local-export:10000",
                "EXPORT_SERVICE_AUTHENTICATED", "false",
                "KESSEL_ENDPOINT", "localhost:7777",
                "RBAC_BASE_ENDPOINT", "http://workspace-override",
                "SWATCH_EXPORT_PSK", "test-psk"));
    assertEquals("http://local-rbac:8080/api/rbac/v1", env.getProperty(RBAC + "url"));
    assertEquals("false", env.getProperty(RBAC + "authenticated"));
    assertEquals("http://local-export:10000", env.getProperty(EXPORT + "url"));
    assertEquals("false", env.getProperty(EXPORT + "authenticated"));
    assertEquals("test-psk", env.getProperty(EXPORT + "psk"));
    assertEquals("localhost:7777", env.getProperty(KESSEL + "endpoint"));
    assertEquals("http://workspace-override", env.getProperty(KESSEL + "rbac-base-endpoint"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"api", "tally", "contracts", "utilization"})
  void deploymentTemplatesWirePortWorkspaceAndCredentials(String service) throws Exception {
    Path path = Path.of("../swatch-" + service + "/deploy/clowdapp.yaml");
    // Loading the template also checks YAML syntax and anchor resolution.
    var template =
        new YamlPropertySourceLoader().load(service, new FileSystemResource(path)).getFirst();
    assertTrue(template.containsProperty("parameters[0].name"));
    String text = Files.readString(path);
    for (String name :
        new String[] {
          "KESSEL_INVENTORY_API_PORT",
          "RBAC_BASE_ENDPOINT",
          "KESSEL_AUTH_ENABLED",
          "KESSEL_AUTH_OIDC_ISSUER"
        }) {
      assertTrue(text.contains("value: ${" + name + "}"), service + ": " + name);
    }
    assertTrue(text.contains("name: KESSEL_AUTH_CLIENT_ID"));
    assertTrue(text.contains("name: KESSEL_AUTH_CLIENT_SECRET"));
    assertFalse(
        text.contains(
            "value: ${KESSEL_AUTH_CLIENT_SECRET}")); // Secret reference, not a template parameter.
  }

  private StandardEnvironment environment(String json, Map<String, Object> overrides)
      throws Exception {
    var env = new StandardEnvironment();
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    env.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
    env.getPropertySources()
        .addLast(
            new ClowderJsonPropertySource(
                new ClowderJson(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    new ObjectMapper())));
    var loader = new YamlPropertySourceLoader();
    for (String profile : new String[] {"api", "worker"}) {
      for (var source :
          loader.load(
              profile,
              new FileSystemResource("../src/main/resources/application-" + profile + ".yaml"))) {
        env.getPropertySources().addLast(source);
      }
    }
    return env;
  }
}
