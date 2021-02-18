/*
 * Copyright (c) 2019 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

/**
 * Provides access to Host database entities.
 */
@SuppressWarnings({"linelength", "indentation"})
public interface HostRepository extends JpaRepository<Host, UUID> {

    /**
     * Find all Hosts by bucket criteria and return a page of TallyHostView objects.
     * A TallyHostView is a Host representation detailing what 'bucket' was applied
     * to the current daily snapshots.
     *
     * @param accountNumber        The account number of the hosts to query (required).
     * @param productId            The bucket product ID to filter Host by (pass null to ignore).
     * @param sla                  The bucket service level to filter Hosts by (pass null to ignore).
     * @param usage                The bucket usage to filter Hosts by (pass null to ignore).
     * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null or empty string to ignore)
     * @param minCores             Filter to Hosts with at least this number of cores.
     * @param minSockets           Filter to Hosts with at least this number of sockets.
     * @param pageable             the current paging info for this query.
     * @return a page of Host entities matching the criteria.
     */
    @Query(
        value = "select b from HostTallyBucket b join fetch b.host h where " +
            "h.accountNumber = :account and " +
            "b.key.productId = :product and " +
            "b.key.sla = :sla and b.key.usage = :usage and " +
            // Have to do the null check first, otherwise the lower in the LIKE clause has issues with datatypes
            "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and " +
            "b.cores >= :minCores and b.sockets >= :minSockets",
        // Because we are using a 'fetch join' to avoid having to lazy load each bucket host,
        // we need to specify how the Page should gets its count when the 'limit' parameter
        // is used.
        countQuery = "select count(b) from HostTallyBucket b join b.host h where " +
            "h.accountNumber = :account and " +
            "b.key.productId = :product and " +
            "b.key.sla = :sla and b.key.usage = :usage and " +
            "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and " +
            "b.cores >= :minCores and b.sockets >= :minSockets"
    )
    Page<TallyHostView> getTallyHostViews(//NOSONAR (exceeds allowed 7 params)
        @Param("account") String accountNumber,
        @Param("product") String productId,
        @Param("sla") ServiceLevel sla,
        @Param("usage") Usage usage,
        @NotNull @Param("displayNameSubstring") String displayNameSubstring,
        @Param("minCores") int minCores,
        @Param("minSockets") int minSockets,
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

    List<Host> findByAccountNumber(String accountNumber);
}
