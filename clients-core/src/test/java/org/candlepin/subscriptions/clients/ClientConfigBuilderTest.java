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
package org.candlepin.subscriptions.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClientConfigBuilderTest {

  private static final ClientConnection V1 =
      new ClientConnection("v1", "http://v1.example", null, null, null, null);

  /** In-cluster ClowdApp default: authenticated=false but V2 is still usable. */
  private static final ClientConnection V2_IN_CLUSTER =
      new ClientConnection("v2", "https://v2.example", null, null, null, false);

  private static final ClientConnection V2_WITHOUT_AUTH_METADATA =
      new ClientConnection("v2", "https://v2.example", null, null, null, null);

  @Test
  void whenBothConfiguredAutoUsesPrimaryWithFallback() {
    ClientConfig config =
        ClientConfigBuilder.with("export-service", "psk", V2_IN_CLUSTER, V1)
            .build(ClientConfigMode.AUTO);

    assertEquals(V2_IN_CLUSTER, config.primary());
    assertEquals(V1, config.fallback());
  }

  @Test
  void whenPrimaryMisconfiguredForcesFallbackIgnoringMode() {
    ClientConfig config =
        ClientConfigBuilder.with("export-service", "psk", V2_WITHOUT_AUTH_METADATA, V1)
            .build(ClientConfigMode.V2);

    assertEquals(V1, config.primary());
    assertNull(config.fallback());
  }

  @Test
  void whenFallbackMissingForcesPrimaryIgnoringMode() {
    ClientConfig config =
        ClientConfigBuilder.with("export-service", "psk", V2_IN_CLUSTER, null)
            .build(ClientConfigMode.V1);

    assertEquals(V2_IN_CLUSTER, config.primary());
    assertNull(config.fallback());
  }

  @Test
  void modeV1UsesOnlyV1WhenBothConfigured() {
    ClientConfig config =
        ClientConfigBuilder.with("export-service", "psk", V2_IN_CLUSTER, V1)
            .build(ClientConfigMode.V1);

    assertEquals(V1, config.primary());
    assertNull(config.fallback());
  }

  @Test
  void modeV2UsesOnlyV2WhenBothConfigured() {
    ClientConfig config =
        ClientConfigBuilder.with("export-service", "psk", V2_IN_CLUSTER, V1)
            .build(ClientConfigMode.V2);

    assertEquals(V2_IN_CLUSTER, config.primary());
    assertNull(config.fallback());
  }

  @Test
  void throwsWhenNeitherConnectionIsUsable() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ClientConfigBuilder.with("export-service", "psk", V2_WITHOUT_AUTH_METADATA, null)
                .build(ClientConfigMode.AUTO));
  }
}
