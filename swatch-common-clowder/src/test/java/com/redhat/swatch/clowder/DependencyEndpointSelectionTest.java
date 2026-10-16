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
package com.redhat.swatch.clowder;

import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_V2;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DependencyEndpointSelectionTest {

  private static final DependencyEndpoint V1 =
      DependencyEndpoint.v1("http://export.svc:10000", "v1-ts", "v1-pw", "PKCS12");
  private static final DependencyEndpoint V2 =
      DependencyEndpoint.of("https://export.remote.example:443", "v2-ts", "v2-pw", "PKCS12", true);

  /** multicluster-TC006 - When V2 is present and available, the client uses V2. */
  @Test
  void autoUsesV2WhenUriAndAuthenticatedPresent() {
    DependencyEndpointSelection selection = DependencyEndpointSelection.select(MODE_AUTO, V1, V2);

    assertEquals(MODE_AUTO, selection.mode());
    assertEquals(VERSION_V2, selection.version());
    assertEquals(V2.uri(), selection.uri());
    assertEquals("v2-ts", selection.trustStorePath());
  }

  /** multicluster-TC001 - When V2 is absent, the client uses V1. */
  @Test
  void autoFallsBackToV1WhenV2Absent() {
    DependencyEndpointSelection selection = DependencyEndpointSelection.select(MODE_AUTO, V1, null);

    assertEquals(VERSION_V1, selection.version());
    assertEquals(V1.uri(), selection.uri());
  }

  /** multicluster-TC003 - When the V2 URI is absent or blank, the client falls back to V1. */
  @Test
  void autoFallsBackToV1WhenV2UriBlank() {
    DependencyEndpointSelection selection =
        DependencyEndpointSelection.select(
            MODE_AUTO, V1, DependencyEndpoint.of("  ", "v2-ts", "v2-pw", "PKCS12", true));

    assertEquals(VERSION_V1, selection.version());
    assertEquals(V1.uri(), selection.uri());
  }

  /**
   * multicluster-TC005 - When V2 metadata is missing authenticated, the client falls back to V1.
   */
  @Test
  void autoFallsBackToV1WhenAuthenticatedMissing() {
    DependencyEndpointSelection selection =
        DependencyEndpointSelection.select(
            MODE_AUTO, V1, DependencyEndpoint.of(V2.uri(), "v2-ts", "v2-pw", "PKCS12", null));

    assertEquals(VERSION_V1, selection.version());
    assertEquals(V1.uri(), selection.uri());
  }

  @Test
  void autoUsesV2WhenAuthenticatedExplicitlyFalse() {
    DependencyEndpointSelection selection =
        DependencyEndpointSelection.select(
            MODE_AUTO, V1, DependencyEndpoint.of(V2.uri(), "v2-ts", "v2-pw", "PKCS12", false));

    assertEquals(VERSION_V2, selection.version());
    assertEquals(V2.uri(), selection.uri());
  }

  /** multicluster-TC002 - Explicit V1 mode ignores V2 metadata. */
  @Test
  void explicitV1IgnoresV2Metadata() {
    DependencyEndpointSelection selection = DependencyEndpointSelection.select(MODE_V1, V1, V2);

    assertEquals(MODE_V1, selection.mode());
    assertEquals(VERSION_V1, selection.version());
    assertEquals(V1.uri(), selection.uri());
  }

  @Test
  void explicitV2RequiresUsableMetadata() {
    assertThrows(
        IllegalStateException.class,
        () ->
            DependencyEndpointSelection.select(
                MODE_V2, V1, DependencyEndpoint.of(V2.uri(), null, null, null, null)));
  }

  @Test
  void explicitV2UsesV2WhenPresent() {
    DependencyEndpointSelection selection = DependencyEndpointSelection.select(MODE_V2, V1, V2);

    assertEquals(MODE_V2, selection.mode());
    assertEquals(VERSION_V2, selection.version());
    assertEquals(V2.uri(), selection.uri());
  }
}
