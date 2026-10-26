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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.security.KesselAuthorizationService;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportBearerHeaderProviderTest {

  @Mock KesselAuthorizationService kesselAuthorizationService;
  @Mock ClientRequestContext requestContext;

  @Test
  void attachesBearerAuthorizationHeader() {
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    when(requestContext.getHeaders()).thenReturn(headers);
    when(kesselAuthorizationService.getAccessToken()).thenReturn("tok-abc");

    new ExportBearerHeaderProvider(kesselAuthorizationService).filter(requestContext);

    assertEquals("Bearer tok-abc", headers.getFirst("Authorization"));
    verify(kesselAuthorizationService).getAccessToken();
  }

  @Test
  void wrapsTokenAcquisitionFailure() {
    when(kesselAuthorizationService.getAccessToken())
        .thenThrow(
            new IllegalStateException(
                "Failed to get OAuth2 access token", new ConnectException("refused")));

    ExportTokenAcquisitionException thrown =
        assertThrows(
            ExportTokenAcquisitionException.class,
            () ->
                new ExportBearerHeaderProvider(kesselAuthorizationService).filter(requestContext));

    assertInstanceOf(IllegalStateException.class, thrown.getCause());
    assertInstanceOf(ConnectException.class, thrown.getCause().getCause());
  }
}
