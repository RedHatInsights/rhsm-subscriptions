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

import org.candlepin.subscriptions.utilization.api.v1.model.VersionInfo;
import org.candlepin.subscriptions.utilization.api.v1.model.VersionInfoBuild;
import org.candlepin.subscriptions.utilization.api.v1.resources.VersionApi;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/** Return the version information for the application */
@Component
public class VersionResource implements VersionApi {

  private final BuildProperties buildProperties;

  public VersionResource(BuildProperties buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Override
  public VersionInfo getVersion() {
    VersionInfoBuild versionInfoBuild = new VersionInfoBuild();
    versionInfoBuild.setVersion(buildProperties.getVersion());
    versionInfoBuild.setArtifact(buildProperties.getArtifact());
    versionInfoBuild.setName(buildProperties.getName());
    versionInfoBuild.setGroup(buildProperties.getGroup());

    VersionInfo versionInfo = new VersionInfo();
    versionInfo.setBuild(versionInfoBuild);
    return versionInfo;
  }
}
