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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.security.HccAuthTokenProvider;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportPskHeaderProviderTest {
  private ExportPskHeaderProvider provider;
  private ClientRequestContext context;
  private MultivaluedHashMap<String, Object> headers;

  @BeforeEach
  void setup() {
    provider = new ExportPskHeaderProvider();
    provider.tokenProvider = mock(HccAuthTokenProvider.class);
    provider.psk = Optional.empty();
    headers = new MultivaluedHashMap<>();
    context = mock(ClientRequestContext.class);
    when(context.getHeaders()).thenReturn(headers);
  }

  @Test
  void authenticatedModeNeedsNoPskAndRemovesAnyStalePsk() {
    provider.authenticated = true;
    headers.putSingle("x-rh-exports-psk", "stale-secret");
    when(provider.tokenProvider.authorizationHeader()).thenReturn("Bearer workload-token");
    provider.filter(context);
    assertEquals("Bearer workload-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    assertFalse(headers.containsKey("x-rh-exports-psk"));
  }

  @Test
  void legacyModeUsesOnlyPskAndDoesNotRequestTokens() {
    provider.psk = Optional.of("legacy-secret");
    headers.putSingle(HttpHeaders.AUTHORIZATION, "stale-token");
    provider.filter(context);
    provider.filter(context);
    assertEquals("legacy-secret", headers.getFirst("x-rh-exports-psk"));
    assertEquals(1, headers.get("x-rh-exports-psk").size());
    assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    verifyNoInteractions(provider.tokenProvider);
  }

  @Test
  void missingLegacyPskFailsBeforeSendingARequest() {
    assertThrows(IllegalStateException.class, () -> provider.filter(context));
    verifyNoInteractions(provider.tokenProvider);
  }

  @Test
  void blankLegacyPskFailsBeforeSendingARequest() {
    provider.psk = Optional.of(" ");
    assertThrows(IllegalStateException.class, () -> provider.filter(context));
  }

  @Test
  void tokenFailureDoesNotFallBackToAnAvailablePsk() {
    provider.authenticated = true;
    provider.psk = Optional.of("legacy-secret");
    when(provider.tokenProvider.authorizationHeader())
        .thenThrow(new IllegalStateException("token unavailable"));
    assertThrows(IllegalStateException.class, () -> provider.filter(context));
    assertFalse(headers.containsKey("x-rh-exports-psk"));
  }

  @Test
  void renewedTokensAreUsedOnSubsequentRequests() {
    provider.authenticated = true;
    when(provider.tokenProvider.authorizationHeader()).thenReturn("Bearer first", "Bearer renewed");
    provider.filter(context);
    assertEquals("Bearer first", headers.getFirst(HttpHeaders.AUTHORIZATION));
    provider.filter(context);
    assertEquals("Bearer renewed", headers.getFirst(HttpHeaders.AUTHORIZATION));
    assertEquals(1, headers.get(HttpHeaders.AUTHORIZATION).size());
  }
}
