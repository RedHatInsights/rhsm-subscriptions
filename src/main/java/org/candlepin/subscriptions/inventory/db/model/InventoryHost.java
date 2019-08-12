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
                @ColumnResult(name = "products"),
                @ColumnResult(name = "sync_timestamp"),
                @ColumnResult(name = "system_profile_cores_per_socket"),
                @ColumnResult(name = "system_profile_sockets"),
                @ColumnResult(name = "qpc_products"),
                @ColumnResult(name = "qpc_product_ids"),
                @ColumnResult(name = "system_profile_product_ids")
            }
        )
    }
)
/* This query is complex so that we can fetch all the product IDs as a comma-delimited string all in one
 * query.  It's inspired by https://dba.stackexchange.com/a/54289. See also
 * https://stackoverflow.com/a/28557803/6124862
 */
@NamedNativeQuery(name = "InventoryHost.getFacts",
    query = "select h.account, h.display_name, " +
        "h.facts->'rhsm'->>'orgId' as org_id, " +
        "h.facts->'rhsm'->>'CPU_CORES' as cores, " +
        "h.facts->'rhsm'->>'CPU_SOCKETS' as sockets, " +
        "h.facts->'qpc'->>'IS_RHEL' as is_rhel, " +
        "h.facts->'rhsm'->>'SYNC_TIMESTAMP' as sync_timestamp, " +
        "h.system_profile_facts->>'cores_per_socket' as system_profile_cores_per_socket, " +
        "h.system_profile_facts->>'number_of_sockets' as system_profile_sockets, " +
        "rhsm_products.products, " +
        "qpc_prods.qpc_products, " +
        "qpc_certs.qpc_product_ids, " +
        "system_profile.system_profile_product_ids " +
        "from hosts h " +
        "cross join lateral ( " +
        "    select string_agg(items, ',') as products " +
        "    from jsonb_array_elements_text(h.facts->'rhsm'->'RH_PROD') as items) rhsm_products " +
        "cross join lateral ( " +
        "    select string_agg(items, ',') as qpc_products " +
        "    from jsonb_array_elements_text(h.facts->'qpc'->'rh_products_installed') as items) qpc_prods " +
        "cross join lateral ( " +
        "    select string_agg(items, ',') as qpc_product_ids " +
        "    from jsonb_array_elements_text(h.facts->'qpc'->'rh_product_certs') as items) qpc_certs " +
        "cross join lateral ( " +
        "    select string_agg(items->>'id', ',') as system_profile_product_ids " +
        "    from jsonb_array_elements(h.system_profile_facts->'installed_products') as items) system_profile " +
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
