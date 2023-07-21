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
package org.candlepin.subscriptions.inventory.db;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Interface that Spring Data will turn into a read-only DAO. */
@SuppressWarnings({"linelength", "indentation"})
public interface InventoryRepository extends Repository<InventoryHost, UUID> {

  @Query(nativeQuery = true)
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READONLY, value = "true")
      })
  Stream<InventoryHostFacts> streamFacts(
      @Param("orgId") String orgId, @Param("culledOffsetDays") Integer culledOffsetDays);

  default Stream<InventoryHostFacts> getFacts(Collection<String> orgIds, Integer culledOffsetDays) {
    return orgIds.stream().flatMap(orgId -> streamFacts(orgId, culledOffsetDays));
  }

  @Query(
      nativeQuery = true,
      value =
          """
          select count(*) from hosts h
          where org_id=:orgId
            and (h.facts->'rhsm'->>'BILLING_MODEL' IS NULL OR h.facts->'rhsm'->>'BILLING_MODEL' <> 'marketplace')
            and (h.system_profile_facts->>'host_type' IS NULL OR h.system_profile_facts->>'host_type' <> 'edge')
            and NOW() < stale_timestamp + make_interval(days => :culledOffsetDays)
          """)
  int activeSystemCountForOrgId(
      @Param("orgId") String orgId, @Param("culledOffsetDays") Integer culledOffsetDays);

  /**
   * Get a mapping of hypervisor ID to associated hypervisor host's subscription-manager ID. If the
   * hypervisor hasn't been reported, then the hyp_subman_id value will be null.
   *
   * @param orgIds the orgIds to filter hosts by.
   * @return a stream of Object[] with each entry representing a hypervisor mapping.
   */
  @Query(
      nativeQuery = true,
      value =
          "select "
              + "distinct h.facts->'rhsm'->>'VM_HOST_UUID' as hyp_id, "
              + "h_.canonical_facts->>'subscription_manager_id' as hyp_subman_id "
              + "from hosts h "
              + "left outer join hosts h_ on h.facts->'rhsm'->>'VM_HOST_UUID' = h_.canonical_facts->>'subscription_manager_id' "
              + "where h.facts->'rhsm'->'VM_HOST_UUID' is not null "
              + "and h.org_id IN (:orgIds)"
              + "union all "
              + "select "
              + "distinct h.facts->'satellite'->>'virtual_host_uuid' as hyp_id, "
              + "h_.canonical_facts->>'subscription_manager_id' as hyp_subman_id "
              + "from hosts h "
              + "left outer join hosts h_ on h.facts->'satellite'->>'virtual_host_uuid' = h_.canonical_facts->>'subscription_manager_id' "
              + "where h.facts->'satellite'->'virtual_host_uuid' is not null "
              + "and h.org_id IN (:orgIds)")
  Stream<Object[]> getReportedHypervisors(@Param("orgIds") Collection<String> orgIds);

  /* NOTE: in below query, ordering is crucial for correct streaming reconciliation of HBI data */
  @Query(
      nativeQuery = true,
      value =
          """
        select
        h.canonical_facts->>'subscription_manager_id' as subscription_manager_id
        from hosts h
        where h.org_id=:orgId
           and (h.facts->'rhsm'->>'BILLING_MODEL' IS NULL OR h.facts->'rhsm'->>'BILLING_MODEL' <> 'marketplace')
           and (h.system_profile_facts->>'host_type' IS NULL OR h.system_profile_facts->>'host_type' <> 'edge')
           and NOW() < stale_timestamp + make_interval(days => :culledOffsetDays)
        -- NOTE: ordering is crucial for correct streaming reconciliation of HBI data
        order by subscription_manager_id
      """)
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READONLY, value = "true")
      })
  Stream<String> streamActiveSubscriptionManagerIds(
      @Param("orgId") String orgId, @Param("culledOffsetDays") Integer culledOffsetDays);
}
