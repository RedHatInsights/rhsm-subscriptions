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

import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clowder.DependencyEndpoint;
import com.redhat.swatch.clowder.DependencyEndpointSelection;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportServiceApiFactoryTest {

  @Mock ExportAuthHeaderProvider authHeaderProvider;

  @Test
  void requireEncryptedTransportAcceptsHttps() {
    assertDoesNotThrow(
        () ->
            ExportServiceApiFactory.requireEncryptedTransport(
                URI.create("https://export.example")));
  }

  @Test
  void requireEncryptedTransportRejectsHttp() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                ExportServiceApiFactory.requireEncryptedTransport(
                    URI.create("http://export.example")));
    assertTrue(ex.getMessage().contains("HTTPS"));
  }

  @Test
  void bearerAuthRejectsHttpEndpointBeforeClientBuild() {
    when(authHeaderProvider.usesBearerAuth()).thenReturn(true);
    ExportServiceApiFactory factory = new ExportServiceApiFactory(authHeaderProvider);
    DependencyEndpointSelection selection =
        new DependencyEndpointSelection(
            MODE_AUTO,
            VERSION_V2,
            DependencyEndpoint.of("http://export.example", "", "", "", true));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> factory.clientFor(selection));
    assertTrue(ex.getMessage().contains("HTTPS"));
  }
}
