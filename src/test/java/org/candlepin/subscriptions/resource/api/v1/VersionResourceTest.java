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
package org.candlepin.subscriptions.resource.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.utilization.api.v1.model.VersionInfo;
import org.candlepin.subscriptions.utilization.api.v1.model.VersionInfoBuild;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
class VersionResourceTest {
  @MockBean BuildProperties buildProperties;

  @Autowired VersionResource versionResource;

  @Test
  void getVersion() {
    when(buildProperties.getVersion()).thenReturn("VERSION");
    when(buildProperties.getArtifact()).thenReturn("ARTIFACT");
    when(buildProperties.getName()).thenReturn("NAME");
    when(buildProperties.getGroup()).thenReturn("GROUP");

    VersionInfo versionInfo = versionResource.getVersion();
    VersionInfoBuild expected = versionInfo.getBuild();
    assertEquals("VERSION", expected.getVersion());
    assertEquals("ARTIFACT", expected.getArtifact());
    assertEquals("NAME", expected.getName());
    assertEquals("GROUP", expected.getGroup());
  }
}
