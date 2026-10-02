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
package com.redhat.swatch.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.enterprise.event.Observes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HccEndpointStartupLoggerTest {
  @Test
  void reportsFinalRestClientOverridesAndResolvedGrpcPort() {
    var logger =
        logger(
            Map.of(
                "RBAC_ENDPOINT",
                "http://legacy-rbac:8000",
                HccEndpointStartupLogger.RBAC_URL,
                "${RBAC_ENDPOINT}/api/rbac/v1",
                "swatch.kessel.endpoint",
                "https://kessel.example:8000",
                "swatch.kessel.inventory-api-port",
                "443",
                "swatch.kessel.rbac-base-endpoint",
                "https://workspace.example/private",
                HccEndpointStartupLogger.EXPORT_URL,
                "https://export.example:443/upload?token=secret"),
            Map.of(
                HccEndpointStartupLogger.RBAC_URL,
                "https://user:password@overridden-rbac.example:8443/api?token=secret"));
    assertEquals(
        Map.of(
            "rbac", "https://overridden-rbac.example:8443",
            "rbac-workspaces", "https://workspace.example",
            "kessel", "kessel.example:443",
            "export", "https://export.example:443"),
        logger.destinations());
  }

  @Test
  void keepsLegacyTargetsAndOmitsExportWhereItIsNotConfigured() {
    var logger =
        logger(
            Map.of(
                "RBAC_ENDPOINT",
                "http://rbac.svc:8000",
                HccEndpointStartupLogger.RBAC_URL,
                "${RBAC_ENDPOINT}/api/rbac/v1",
                "swatch.kessel.endpoint",
                "kessel.svc:9000"),
            Map.of());
    assertEquals(
        Map.of(
            "rbac", "http://rbac.svc:8000",
            "rbac-workspaces", "http://rbac.svc:8000",
            "kessel", "kessel.svc:9000"),
        logger.destinations());
  }

  @Test
  void restClientUriOverridesTakePrecedenceOverUrls() {
    var logger =
        logger(
            Map.of(
                HccEndpointStartupLogger.RBAC_URL, "http://old-rbac:8000",
                HccEndpointStartupLogger.EXPORT_URL, "http://old-export:10000"),
            Map.of(
                HccEndpointStartupLogger.RBAC_URL.replace(".url", ".uri"),
                "https://rbac-override.example:443",
                HccEndpointStartupLogger.EXPORT_URL.replace(".url", ".uri"),
                "https://export-override.example:443"));
    assertEquals("https://rbac-override.example:443", logger.destinations().get("rbac"));
    assertEquals("https://export-override.example:443", logger.destinations().get("export"));
  }

  @Test
  void reportsExportWhenOnlyUriIsConfigured() {
    var logger =
        logger(
            Map.of(
                "swatch-common-security.include-rbac",
                "false",
                HccEndpointStartupLogger.EXPORT_URL.replace(".url", ".uri"),
                "https://export.example:443"),
            Map.of());
    assertEquals(Map.of("export", "https://export.example:443"), logger.destinations());
  }

  @Test
  void unresolvedOrAbsentValuesDoNotBreakDiagnosticLogging() {
    var logger =
        logger(
            Map.of(
                HccEndpointStartupLogger.RBAC_URL,
                "${missing}",
                "swatch.kessel.endpoint",
                "https://:8000",
                HccEndpointStartupLogger.EXPORT_URL,
                ""),
            Map.of());
    assertEquals(
        Map.of(
            "rbac", "<unresolved>",
            "rbac-workspaces", "<not-configured>",
            "kessel", "<unresolved>",
            "export", "<not-configured>"),
        logger.destinations());
    logger.onStartup(new StartupEvent());
  }

  @Test
  void excludesDisabledSecurityIntegrationButStillReportsExport() {
    var logger =
        logger(
            Map.of(
                "swatch-common-security.include-rbac",
                "false",
                HccEndpointStartupLogger.EXPORT_URL,
                "http://export.svc:10000"),
            Map.of());
    assertEquals(Map.of("export", "http://export.svc:10000"), logger.destinations());
  }

  @Test
  void observesStartupRatherThanWaitingForFirstClientRequest() throws Exception {
    var startup = HccEndpointStartupLogger.class.getDeclaredMethod("onStartup", StartupEvent.class);
    assertTrue(startup.getParameters()[0].isAnnotationPresent(Observes.class));
  }

  private HccEndpointStartupLogger logger(
      Map<String, String> values, Map<String, String> overrides) {
    var config =
        new SmallRyeConfigBuilder()
            .addDefaultInterceptors()
            .withMapping(KesselProperties.class)
            .withSources(
                new PropertiesConfigSource(values, "application", 100),
                new PropertiesConfigSource(overrides, "overrides", 300))
            .build();
    var logger = new HccEndpointStartupLogger();
    logger.config = config;
    logger.kessel = config.getConfigMapping(KesselProperties.class);
    return logger;
  }
}
