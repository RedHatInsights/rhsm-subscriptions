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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KesselPropertiesTest {

  @Test
  void mappedPortAndAuthenticationOverridesAreApplied() {
    var config =
        new SmallRyeConfigBuilder()
            .withMapping(KesselProperties.class)
            .withSources(
                new PropertiesConfigSource(
                    Map.of(
                        "swatch.kessel.endpoint", "https://kessel.example:8000",
                        "swatch.kessel.inventory-api-port", "443",
                        "swatch.kessel.auth-enabled", "true"),
                    "test",
                    100))
            .build();
    var props = config.getConfigMapping(KesselProperties.class);
    assertEquals("kessel.example:443", props.resolvedEndpoint());
    org.junit.jupiter.api.Assertions.assertTrue(props.authEnabled());
  }

  @Test
  void resolvedEndpointPassesThroughSchemelessValues() {
    var props = withEndpoint("localhost:9000");
    assertEquals("localhost:9000", props.resolvedEndpoint());
  }

  @Test
  void resolvedEndpointPassesThroughSchemelessHostPort() {
    var props = withEndpoint("wiremock-service:8000");
    assertEquals("wiremock-service:8000", props.resolvedEndpoint());
  }

  @Test
  void resolvedEndpointExtractsHostFromHttpUrl() {
    var props = withEndpoint("http://kessel-inventory-api.namespace.svc:8000");
    assertEquals(
        "kessel-inventory-api.namespace.svc:" + KesselProperties.GRPC_PORT,
        props.resolvedEndpoint());
  }

  @Test
  void resolvedEndpointExtractsHostFromHttpsUrl() {
    var props = withEndpoint("https://kessel-inventory-api.namespace.svc:8000");
    assertEquals(
        "kessel-inventory-api.namespace.svc:" + KesselProperties.GRPC_PORT,
        props.resolvedEndpoint());
  }

  @Test
  void resolvedEndpointThrowsOnMalformedUrl() {
    var props = withEndpoint("http://:8000");
    assertThrows(IllegalArgumentException.class, props::resolvedEndpoint);
  }

  private static KesselProperties withEndpoint(String endpoint) {
    return new KesselProperties() {
      @Override
      public int inventoryApiPort() {
        return GRPC_PORT;
      }

      @Override
      public boolean authEnabled() {
        return false;
      }

      @Override
      public String endpoint() {
        return endpoint;
      }

      @Override
      public boolean insecure() {
        return true;
      }

      @Override
      public long timeoutMs() {
        return 5000;
      }

      @Override
      public Optional<String> rbacBaseEndpoint() {
        return Optional.of("http://localhost:8080");
      }

      @Override
      public Optional<String> authOidcIssuer() {
        return Optional.empty();
      }

      @Override
      public Optional<String> authClientId() {
        return Optional.empty();
      }

      @Override
      public Optional<String> authClientSecret() {
        return Optional.empty();
      }
    };
  }
}
