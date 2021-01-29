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
import org.candlepin.subscriptions.db.model.HostBucketKey_;
import org.candlepin.subscriptions.db.model.HostTallyBucket_;
import org.candlepin.subscriptions.db.model.Host_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.TallyHostViewImpl;
import org.candlepin.subscriptions.db.model.Usage;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

/**
 * Provides access to Host database entities.
 */
@SuppressWarnings({"linelength", "indentation"})
public interface HostRepository extends JpaRepository<Host, UUID>, JpaSpecificationExecutor<Host> {

    @Override
    @EntityGraph(attributePaths = {"buckets"})
    Page<Host> findAll(Specification<Host> specification, Pageable pageable);

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
    default Page<TallyHostView> getTallyHostViews(//NOSONAR (exceeds allowed 7 params)
        @Param("account") String accountNumber, @Param("product") String productId,
        @Param("sla") ServiceLevel sla, @Param("usage") Usage usage,
        @NotNull @Param("displayNameSubstring") String displayNameSubstring, @Param("minCores") int minCores,
        @Param("minSockets") int minSockets, Pageable pageable) {

        HostSpecification searchCriteria = new HostSpecification();

        searchCriteria.add(new SearchCriteria(Host_.ACCOUNT_NUMBER, accountNumber, SearchOperation.EQUAL));
        searchCriteria.add(new SearchCriteria(HostBucketKey_.PRODUCT_ID, productId, SearchOperation.EQUAL));
        searchCriteria.add(new SearchCriteria(HostBucketKey_.SLA, sla, SearchOperation.EQUAL));
        searchCriteria.add(new SearchCriteria(HostBucketKey_.USAGE, usage, SearchOperation.EQUAL));
        searchCriteria.add(new SearchCriteria(Host_.DISPLAY_NAME, displayNameSubstring, SearchOperation.CONTAINS));
        searchCriteria.add(new SearchCriteria(HostTallyBucket_.CORES, minCores,
            SearchOperation.GREATER_THAN_EQUAL));
        searchCriteria.add(new SearchCriteria(HostTallyBucket_.SOCKETS, minSockets,
            SearchOperation.GREATER_THAN_EQUAL));

        Page<Host> results = findAll(searchCriteria, pageable);

        List<TallyHostView> asView = results.getContent().stream().map(TallyHostViewImpl::new).collect(Collectors.toList());

        return new PageImpl<>(asView, pageable, results.getTotalElements());
    }

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

    @Transactional
    @Modifying
    @Query("delete from Host where account_number in (:accounts)")
    int deleteByAccountNumberIn(@Param("accounts") Collection<String> accounts);

}
