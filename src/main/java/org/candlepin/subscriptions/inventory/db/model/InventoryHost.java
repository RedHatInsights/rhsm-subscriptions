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
import lombok.Getter;
import lombok.Setter;

/** Represents a host entity stored in the inventory service's database. */
@SuppressWarnings({"indentation", "linelength"})
@Entity
@Table(name = "hosts")
@SqlResultSetMapping(
    name = "inventoryHostFactsMapping",
    classes = {
      @ConstructorResult(
          targetClass = InventoryHostFacts.class,
          columns = {
            @ColumnResult(name = "inventory_id", type = UUID.class),
            @ColumnResult(name = "modified_on", type = OffsetDateTime.class),
            @ColumnResult(name = "account"),
            @ColumnResult(name = "display_name"),
            @ColumnResult(name = "org_id"),
            @ColumnResult(name = "products"),
            @ColumnResult(name = "sync_timestamp"),
            @ColumnResult(name = "system_profile_infrastructure_type"),
            @ColumnResult(name = "system_profile_cores_per_socket"),
            @ColumnResult(name = "system_profile_sockets"),
            @ColumnResult(name = "system_profile_arch"),
            @ColumnResult(name = "is_marketplace"),
            @ColumnResult(name = "qpc_products"),
            @ColumnResult(name = "qpc_product_ids"),
            @ColumnResult(name = "system_profile_product_ids"),
            @ColumnResult(name = "syspurpose_role"),
            @ColumnResult(name = "syspurpose_sla"),
            @ColumnResult(name = "syspurpose_usage"),
            @ColumnResult(name = "syspurpose_units"),
            @ColumnResult(name = "billing_model"),
            @ColumnResult(name = "is_virtual"),
            @ColumnResult(name = "hypervisor_uuid"),
            @ColumnResult(name = "satellite_hypervisor_uuid"),
            @ColumnResult(name = "satellite_role"),
            @ColumnResult(name = "satellite_sla"),
            @ColumnResult(name = "satellite_usage"),
            @ColumnResult(name = "guest_id"),
            @ColumnResult(name = "subscription_manager_id"),
            @ColumnResult(name = "insights_id"),
            @ColumnResult(name = "cloud_provider"),
            @ColumnResult(name = "stale_timestamp", type = OffsetDateTime.class)
          })
    })
/* This query is complex so that we can fetch all the product IDs as a comma-delimited string all in one
 * query.  It's inspired by https://dba.stackexchange.com/a/54289. See also
 * https://stackoverflow.com/a/28557803/6124862
 *
 * To add new query date must make updates in the following order
 * First step -> update queries with the following structure (h.facts ->> rhsm ->> new field as new field,)
 * check insights-host-inventory/blob/master/swagger/system_profile.spec.yaml for queries on HBI Database.
 * Second step: Add new field as a ColumnResult
 * Third step : update inventory host facts constructor with new column
 */
@NamedNativeQuery(
    name = "InventoryHost.getFacts",
    query =
        "select h.id as inventory_id, h.modified_on, h.account, h.display_name, "
            + "h.facts->'rhsm'->>'orgId' as org_id, "
            + "h.facts->'rhsm'->>'IS_VIRTUAL' as is_virtual, "
            + "h.facts->'rhsm'->>'VM_HOST_UUID' as hypervisor_uuid, "
            + "h.facts->'satellite'->>'virtual_host_uuid' as satellite_hypervisor_uuid, "
            + "h.facts->'satellite'->>'system_purpose_role' as satellite_role, "
            + "h.facts->'satellite'->>'system_purpose_sla' as satellite_sla, "
            + "h.facts->'satellite'->>'system_purpose_usage' as satellite_usage, "
            + "h.facts->'rhsm'->>'GUEST_ID' as guest_id, "
            + "h.facts->'rhsm'->>'SYNC_TIMESTAMP' as sync_timestamp, "
            + "h.facts->'rhsm'->>'SYSPURPOSE_ROLE' as syspurpose_role, "
            + "h.facts->'rhsm'->>'SYSPURPOSE_SLA' as syspurpose_sla, "
            + "h.facts->'rhsm'->>'SYSPURPOSE_USAGE' as syspurpose_usage, "
            + "h.facts->'rhsm'->>'SYSPURPOSE_UNITS' as syspurpose_units, "
            + "h.facts->'rhsm'->>'BILLING_MODEL' as  billing_model, "
            + "h.facts->'qpc'->>'IS_RHEL' as is_rhel, "
            + "h.system_profile_facts->>'infrastructure_type' as system_profile_infrastructure_type, "
            + "h.system_profile_facts->>'cores_per_socket' as system_profile_cores_per_socket, "
            + "h.system_profile_facts->>'number_of_sockets' as system_profile_sockets, "
            + "h.system_profile_facts->>'cloud_provider' as cloud_provider, "
            + "h.system_profile_facts->>'arch' as system_profile_arch, "
            + "h.system_profile_facts->>'is_marketplace' as is_marketplace, "
            + "h.canonical_facts->>'subscription_manager_id' as subscription_manager_id, "
            + "h.canonical_facts->>'insights_id' as insights_id, "
            + "rhsm_products.products, "
            + "qpc_prods.qpc_products, "
            + "qpc_certs.qpc_product_ids, "
            + "system_profile.system_profile_product_ids, "
            + "h.stale_timestamp "
            + "from hosts h "
            + "cross join lateral ( "
            + "    select string_agg(items, ',') as products "
            + "    from jsonb_array_elements_text(h.facts->'rhsm'->'RH_PROD') as items) rhsm_products "
            + "cross join lateral ( "
            + "    select string_agg(items, ',') as qpc_products "
            + "    from jsonb_array_elements_text(h.facts->'qpc'->'rh_products_installed') as items) qpc_prods "
            + "cross join lateral ( "
            + "    select string_agg(items, ',') as qpc_product_ids "
            + "    from jsonb_array_elements_text(h.facts->'qpc'->'rh_product_certs') as items) qpc_certs "
            + "cross join lateral ( "
            + "    select string_agg(items->>'id', ',') as system_profile_product_ids "
            + "    from jsonb_array_elements(h.system_profile_facts->'installed_products') as items) system_profile "
            + "where account IN (:accounts)"
            + "   and (h.facts->'rhsm'->>'BILLING_MODEL' IS NULL OR h.facts->'rhsm'->>'BILLING_MODEL' <> 'marketplace')"
            + "   and (h.system_profile_facts->>'host_type' IS NULL OR h.system_profile_facts->>'host_type' <> 'edge')"
            + "   and (stale_timestamp is null "
            + "   or  (NOW() < stale_timestamp + make_interval(days => :culledOffsetDays)))",
    resultSetMapping = "inventoryHostFactsMapping")
@Getter
@Setter
public class InventoryHost implements Serializable {

  @Id private UUID id;

  private String account;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "created_on")
  private OffsetDateTime createdOn;

  @Column(name = "modified_on")
  private OffsetDateTime modifiedOn;
}
