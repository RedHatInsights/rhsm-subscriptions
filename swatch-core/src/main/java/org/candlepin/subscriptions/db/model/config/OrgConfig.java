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
package org.candlepin.subscriptions.db.model.config;

import java.time.OffsetDateTime;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** Represents the configuration properties for an organization (owner). */
@Entity
@Table(name = "org_config")
public class OrgConfig extends BaseConfig {

  @Id
  @Column(name = "org_id")
  private String orgId;

  public OrgConfig() {}

  public OrgConfig(String orgId) {
    this.orgId = orgId;
  }

  public static OrgConfig fromInternalApi(String orgId, OffsetDateTime timestamp) {
    OrgConfig orgConfig = new OrgConfig(orgId);
    orgConfig.setOptInType(OptInType.INTERNAL_API);
    orgConfig.setCreated(timestamp);
    orgConfig.setUpdated(timestamp);
    return orgConfig;
  }

  public String getOrgId() {
    return orgId;
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof OrgConfig)) {
      return false;
    }

    if (!super.equals(o)) {
      return false;
    }

    OrgConfig orgConfig = (OrgConfig) o;
    return Objects.equals(orgId, orgConfig.orgId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), orgId);
  }
}
