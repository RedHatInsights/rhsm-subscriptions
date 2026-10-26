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
package com.redhat.swatch.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OidcTokenProviderTest {

  @Test
  void missingConfigFailsWithoutCallingFactory() {
    AtomicInteger calls = new AtomicInteger();
    OidcTokenProvider provider =
        new OidcTokenProvider(
            null,
            "id",
            "secret",
            (issuer, id, secret) -> {
              calls.incrementAndGet();
              return () -> "unused";
            });

    assertThrows(IllegalStateException.class, provider::getAccessToken);
    assertEquals(0, calls.get());
  }

  @Test
  void discoveryFailureIsRetriedOnNextCall() {
    AtomicInteger calls = new AtomicInteger();
    OidcTokenProvider provider =
        new OidcTokenProvider(
            "https://issuer.example",
            "id",
            "secret",
            (issuer, id, secret) -> {
              if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("oidc unavailable");
              }
              return () -> "access-token";
            });

    assertThrows(IllegalStateException.class, provider::getAccessToken);
    assertEquals("access-token", provider.getAccessToken());
    assertEquals("access-token", provider.getAccessToken());
    assertEquals(2, calls.get());
  }

  @Test
  void successfulDiscoveryIsCached() {
    AtomicInteger calls = new AtomicInteger();
    OidcTokenProvider provider =
        new OidcTokenProvider(
            "https://issuer.example",
            "id",
            "secret",
            (issuer, id, secret) -> {
              calls.incrementAndGet();
              return () -> "cached-token";
            });

    assertEquals("cached-token", provider.getAccessToken());
    assertEquals("cached-token", provider.getAccessToken());
    assertEquals(1, calls.get());
  }
}
