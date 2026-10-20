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

import static com.redhat.swatch.clowder.ClowderDependencyInfoContributor.SECTION;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClowderDependencyInfoContributorTest {

  @Mock Instance<DependencyEndpointResolver> resolvers;

  @Test
  void contributesSelectionsWithoutSecrets() {
    DependencyEndpointResolver resolver =
        new DependencyEndpointResolver(
            "export-service",
            MODE_AUTO,
            DependencyEndpoint.v1("http://v1.example", "ts", "pw", "PKCS12"),
            DependencyEndpoint.of(
                "https://alice:s3cret@v2.example:8443/export?x=1#frag",
                "ts",
                "pw",
                "PKCS12",
                true));
    when(resolvers.iterator()).thenReturn(List.of(resolver).iterator());

    ClowderDependencyInfoContributor info = new ClowderDependencyInfoContributor(resolvers);

    assertEquals(SECTION, info.name());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) info.data();
    @SuppressWarnings("unchecked")
    Map<String, Object> exportService = (Map<String, Object>) data.get("export-service");

    assertEquals(MODE_AUTO, exportService.get("mode"));
    assertEquals(VERSION_V2, exportService.get("version"));
    assertEquals("https://v2.example:8443/export?x=1#frag", exportService.get("uri"));
    assertEquals(3, exportService.size());
  }
}
