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
package org.candlepin.subscriptions.inventory.db.model;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ColumnResult;
import javax.persistence.ConstructorResult;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.Table;


/**
 * Represents a host entity stored in the inventory service's database.
 */
@SuppressWarnings({"indentation", "linelength"})
@Entity
@Table(name = "hosts")
@SqlResultSetMapping(
    name = "inventoryHostFactsMapping",
    classes = {
        @ConstructorResult(
            targetClass = InventoryHostFacts.class,
            columns = {
                @ColumnResult(name = "account"),
                @ColumnResult(name = "display_name"),
                @ColumnResult(name = "org_id"),
                @ColumnResult(name = "cores"),
                @ColumnResult(name = "sockets"),
                @ColumnResult(name = "is_rhel"),
                @ColumnResult(name = "products"),
                @ColumnResult(name = "sync_timestamp")
            }
        )
    }
)
@NamedNativeQuery(name = "InventoryHost.getFacts",
    query =
        "select " +
            "account, display_name, facts->'rhsm'->>'orgId' as org_id, facts->'rhsm'->>'CPU_CORES' as cores, " +
            "facts->'rhsm'->>'CPU_SOCKETS' as sockets, facts->'qpc'->>'IS_RHEL' as is_rhel, " +
            "facts->'rhsm'->>'RH_PROD' as products, facts->'rhsm'->>'SYNC_TIMESTAMP' as sync_timestamp " +
        "from hosts " +
        "where account IN (:accounts)",
    resultSetMapping = "inventoryHostFactsMapping")
public class InventoryHost implements Serializable {

    @Id
    private UUID id;

    private String account;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_on")
    private OffsetDateTime createdOn;

    @Column(name = "modified_on")
    private OffsetDateTime modifiedOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(OffsetDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public OffsetDateTime getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(OffsetDateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

}
