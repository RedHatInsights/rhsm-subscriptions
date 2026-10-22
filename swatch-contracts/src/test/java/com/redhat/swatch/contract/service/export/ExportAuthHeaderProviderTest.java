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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.security.KesselAuthorizationService;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportAuthHeaderProviderTest {

  @Mock KesselAuthorizationService kesselAuthorizationService;
  @Mock ClientRequestContext requestContext;

  private ExportAuthHeaderProvider provider;
  private MultivaluedMap<String, Object> headers;

  @BeforeEach
  void setUp() {
    provider = new ExportAuthHeaderProvider(kesselAuthorizationService);
    headers = new MultivaluedHashMap<>();
  }

  private void givenRequestHeaders() {
    when(requestContext.getHeaders()).thenReturn(headers);
  }

  @Test
  void authenticatedTrueAttachesBearer() {
    givenRequestHeaders();
    provider.authenticated = Optional.of("true");
    when(kesselAuthorizationService.getAccessToken()).thenReturn("tok-abc");

    provider.filter(requestContext);

    assertEquals("Bearer tok-abc", headers.getFirst("Authorization"));
    assertNull(headers.getFirst("x-rh-exports-psk"));
    verify(kesselAuthorizationService).getAccessToken();
  }

  @Test
  void authenticatedFalseAttachesPsk() {
    givenRequestHeaders();
    provider.authenticated = Optional.of("false");
    provider.psk = "test-psk";

    provider.filter(requestContext);

    assertEquals("test-psk", headers.getFirst("x-rh-exports-psk"));
    assertNull(headers.getFirst("Authorization"));
    verifyNoInteractions(kesselAuthorizationService);
  }

  @Test
  void authenticatedAbsentAttachesPsk() {
    givenRequestHeaders();
    provider.authenticated = Optional.empty();
    provider.psk = "test-psk";

    provider.filter(requestContext);

    assertEquals("test-psk", headers.getFirst("x-rh-exports-psk"));
    assertNull(headers.getFirst("Authorization"));
    verifyNoInteractions(kesselAuthorizationService);
  }

  @Test
  void useBearerAuthParsesConfig() {
    assertTrue(ExportAuthHeaderProvider.useBearerAuth(Optional.of("true")));
    assertFalse(ExportAuthHeaderProvider.useBearerAuth(Optional.of("false")));
    assertFalse(ExportAuthHeaderProvider.useBearerAuth(Optional.empty()));
    assertFalse(ExportAuthHeaderProvider.useBearerAuth(Optional.of("")));
  }
}
