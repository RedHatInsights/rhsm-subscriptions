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
import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clowder.DependencyEndpoint;
import com.redhat.swatch.clowder.DependencyEndpointSelection;
import org.junit.jupiter.api.Test;

class ExportServiceApiFactoryAuthTest {

  @Test
  void authenticatedTrueUsesBearer() {
    assertTrue(
        ExportServiceApiFactory.useBearerAuth(
            new DependencyEndpointSelection(
                MODE_AUTO, VERSION_V2, DependencyEndpoint.of("https://export", "", "", "", true))));
  }

  @Test
  void authenticatedFalseUsesPsk() {
    assertFalse(
        ExportServiceApiFactory.useBearerAuth(
            new DependencyEndpointSelection(
                MODE_AUTO,
                VERSION_V2,
                DependencyEndpoint.of("https://export", "", "", "", false))));
  }

  @Test
  void v1NullAuthenticatedUsesPsk() {
    assertFalse(
        ExportServiceApiFactory.useBearerAuth(
            new DependencyEndpointSelection(
                MODE_AUTO, VERSION_V1, DependencyEndpoint.v1("http://export", null, null, null))));
  }

  @Test
  void forcedV1UsesPskEvenWhenV2MetadataWouldBeAuthenticated() {
    DependencyEndpointSelection forcedV1 =
        DependencyEndpointSelection.select(
            MODE_V1,
            DependencyEndpoint.v1("http://export-v1", null, null, null),
            DependencyEndpoint.of("https://export-v2", "", "", "", true));
    assertFalse(ExportServiceApiFactory.useBearerAuth(forcedV1));
  }

  @Test
  void stickyV1FallbackSelectionUsesPsk() {
    assertFalse(
        ExportServiceApiFactory.useBearerAuth(
            DependencyEndpointSelection.v1Endpoint(
                DependencyEndpoint.v1("http://export-v1", null, null, null))));
  }
}
