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
package com.redhat.swatch.kessel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointLogSanitizerTest {
  @ParameterizedTest
  @CsvSource({
    "https://rbac.example:443/api/rbac/v1, https://rbac.example:443",
    "http://rbac.svc:8000, http://rbac.svc:8000",
    "https://user:password@export.example/upload?token=secret#private, https://export.example",
    "kessel.example:443, kessel.example:443",
    "user:password@kessel.example:443?token=secret, kessel.example:443",
    "https://[2001:db8::1]:8443/api, https://[2001:db8::1]:8443",
    "[2001:db8::1]:9000, [2001:db8::1]:9000",
    "dns:///kessel.example:443?token=secret, dns:///kessel.example:443",
    "https://rbac.example/private-secret-path, https://rbac.example",
    "https://rbac.example/%0Aprivate, https://rbac.example"
  })
  void logsOnlySchemeHostAndPort(String endpoint, String expected) {
    assertEquals(expected, EndpointLogSanitizer.destination(endpoint));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void absentEndpointIsExplicit(String endpoint) {
    assertEquals("<not-configured>", EndpointLogSanitizer.destination(endpoint));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://user:secret@",
        "not a URL secret",
        "https://rbac.example\nsecret",
        "${missing-secret}",
        "file:///private/credentials",
        "dns:///",
        "https://rbac.example:invalid"
      })
  void malformedEndpointsNeverEchoInput(String endpoint) {
    assertEquals("<unresolved>", EndpointLogSanitizer.destination(endpoint));
  }

  @Test
  void failedResolutionDoesNotFailStartupOrExposeExceptionText() {
    assertEquals(
        "<unresolved>",
        EndpointLogSanitizer.resolve(
            () -> {
              throw new IllegalArgumentException("secret configuration value");
            }));
  }
}
