/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Represents the configuration properties for an organization (owner).
 */
@Entity
@Table(name = "org_config")
public class OrgConfig implements Serializable {

    @Id
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "sync_enabled")
    private Boolean syncEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "opt_in_type")
    private OptInType optInType;

    @Column(name = "created")
    private OffsetDateTime created;

    @Column(name = "updated")
    private OffsetDateTime updated;

    public OrgConfig() {
    }

    public OrgConfig(String orgId) {
        this.orgId = orgId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Boolean getSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(Boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public OptInType getOptInType() {
        return optInType;
    }

    public void setOptInType(OptInType optInType) {
        this.optInType = optInType;
    }

    public OffsetDateTime getCreated() {
        return created;
    }

    public void setCreated(OffsetDateTime created) {
        this.created = created;
    }

    public OffsetDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OrgConfig)) {
            return false;
        }

        OrgConfig that = (OrgConfig) o;
        return orgId.equals(that.orgId) &&
            syncEnabled.equals(that.syncEnabled) &&
            optInType == that.optInType &&
            created.equals(that.created) &&
            updated.equals(that.updated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, syncEnabled, optInType, created, updated);
    }

}
