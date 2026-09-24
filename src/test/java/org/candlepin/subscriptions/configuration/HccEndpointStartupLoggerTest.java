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

import java.time.Duration;
import java.util.Map;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.rbac.KesselProperties;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class HccEndpointStartupLoggerTest {
  @Test
  void logsBoundDestinationsOnStartupWithoutCreatingClients(CapturedOutput output) {
    var rbac = new RbacProperties();
    rbac.setUrl("https://user:password@rbac.example:443/api/rbac/v1?token=secret");
    var kessel = new KesselProperties();
    kessel.setEndpoint("kessel.example:443");
    kessel.setRbacBaseEndpoint("https://workspaces.example/private-path");
    kessel.setAuthClientSecret("do-not-log-this");
    var export = new HttpClientProperties();
    export.setUrl("https://export.example:8443/upload?token=export-secret");
    export.setPsk("do-not-log-psk");

    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          HccEndpointStartupLogger.class, () -> new HccEndpointStartupLogger(rbac, kessel, export));
      context.refresh();
      assertFalse(output.getAll().contains("HCC endpoint resolved:"));
      context.publishEvent(
          new ApplicationReadyEvent(
              new SpringApplication(), new String[0], context, Duration.ZERO));
    }

    assertTrue(output.getAll().contains("service=rbac, destination=https://rbac.example:443"));
    assertTrue(output.getAll().contains("service=kessel, destination=kessel.example:443"));
    assertTrue(
        output
            .getAll()
            .contains("service=rbac-workspaces, destination=https://workspaces.example"));
    assertTrue(output.getAll().contains("service=export, destination=https://export.example:8443"));
    assertFalse(output.getAll().contains("password"));
    assertFalse(output.getAll().contains("secret"));
    assertFalse(output.getAll().contains("do-not-log"));
    assertFalse(output.getAll().contains("private-path"));
  }

  @Test
  void preservesLegacyDestinationsAndWorkspaceFallback() {
    var rbac = new RbacProperties();
    rbac.setUrl("http://rbac.svc:8000/api/rbac/v1");
    var kessel = new KesselProperties();
    kessel.setEndpoint("kessel.svc:9000");
    kessel.setRbacBaseEndpoint(" ");
    assertEquals(
        Map.of(
            "rbac", "http://rbac.svc:8000",
            "rbac-workspaces", "http://rbac.svc:8000",
            "kessel", "kessel.svc:9000"),
        new HccEndpointStartupLogger(rbac, kessel, null).destinations());
  }

  @Test
  void distinguishesStubsAndMissingOptionalServices() {
    var rbac = new RbacProperties();
    rbac.setUseStub(true);
    var export = new HttpClientProperties();
    export.setUseStub(true);
    assertEquals(
        Map.of("rbac", "<stub>", "export", "<stub>"),
        new HccEndpointStartupLogger(rbac, null, export).destinations());
    assertTrue(new HccEndpointStartupLogger(null, null, null).destinations().isEmpty());
  }
}
