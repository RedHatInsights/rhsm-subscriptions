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
package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Provides access to Host database entities.
 */
public interface HostRepository extends JpaRepository<Host, String> {

    /**
     * Find all Hosts by bucket criteria. The 'accountNumber parameter is mandatory,
     * however, passing null for any bucket property will be ignored in the query.
     *
     * @param accountNumber The account number of the hosts to query (required).
     * @param productId The bucket product ID to filter Host by (pass null to ignore).
     * @param sla The bucket service level to filter Hosts by (pass null to ignore).
     * @param asHypervisor Was the host treated as a hypervisor when tallying for this bucket
     *                     (pass null to ignore)?
     * @param isGuest Is the host a guest? If specified, filters to guest/non-guest status.
     * @param pageable the current paging info for this query.
     * @return a page of Host entities matching the criteria.
     */
    @Query(
        "select distinct h from Host h join h.buckets b where " +
        "h.accountNumber = :account and " +
        "(:product is null or b.key.productId = :product) and " +
        "(:sla is null or b.key.sla = :sla) and " +
        "(:usage is null or b.key.usage = :usage) and " +
        "(:is_guest is null or h.guest = :is_guest) and " +
        "(:as_hypervisor is null or b.key.asHypervisor = :as_hypervisor)"
    )
    Page<Host> getHostsByBucketCriteria(
        @Param("account") String accountNumber,
        @Param("product") String productId,
        @Param("sla") ServiceLevel sla,
        @Param("usage") Usage usage,
        @Param("as_hypervisor") Boolean asHypervisor,
        @Param("is_guest") Boolean isGuest,
        Pageable pageable
    );

    @Query(
        "select distinct h from Host h where " +
        "h.accountNumber = :account and " +
        "h.hypervisorUuid = :hypervisor_id"
    )
    Page<Host> getHostsByHypervisor(
        @Param("account") String accountNumber,
        @Param("hypervisor_id") String hypervisorId,
        Pageable pageable
    );
}
