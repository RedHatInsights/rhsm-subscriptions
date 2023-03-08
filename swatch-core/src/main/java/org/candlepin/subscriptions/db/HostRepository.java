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
package org.candlepin.subscriptions.db;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.persistence.QueryHint;
import javax.validation.constraints.NotNull;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey_;
import org.candlepin.subscriptions.db.model.HostTallyBucket_;
import org.candlepin.subscriptions.db.model.Host_;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

/** Provides access to Host database entities. */
@SuppressWarnings({"linelength", "indentation"})
public interface HostRepository
    extends JpaRepository<Host, UUID>, JpaSpecificationExecutor<Host>, TagProfileLookup {

  /* NOTE: in below query, ordering is crucial for correct streaming reconciliation of HBI data */
  @Query(
      value =
          """
      select
      h from Host h
      left join fetch h.measurements
      left join fetch h.buckets
      left join fetch h.monthlyTotals
      where h.orgId=:orgId
        and h.instanceType='HBI_HOST'
      order by coalesce(h.hypervisorUuid, h.subscriptionManagerId), h.hypervisorUuid, h.inventoryId
          """)
  @QueryHints(
      value = {
        @QueryHint(name = HINT_FETCH_SIZE, value = "1024"),
        @QueryHint(name = HINT_READONLY, value = "true")
      })
  Stream<Host> streamHbiHostsByOrgId(@Param("orgId") String orgId);

  /**
   * Find all Hosts by bucket criteria and return a page of TallyHostView objects. A TallyHostView
   * is a Host representation detailing what 'bucket' was applied to the current daily snapshots.
   *
   * @param orgId The orgId of the hosts to query (required).
   * @param productId The bucket product ID to filter Host by (pass null to ignore).
   * @param sla The bucket service level to filter Hosts by (pass null to ignore).
   * @param usage The bucket usage to filter Hosts by (pass null to ignore).
   * @param billingProvider The bucket billingProvider to filter Hosts by (pass null to ignore).
   * @param billingAccountId The bucket billingAccountId to filter Hosts by (pass null to ignore).
   * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null
   *     or empty string to ignore)
   * @param minCores Filter to Hosts with at least this number of cores.
   * @param minSockets Filter to Hosts with at least this number of sockets.
   * @param pageable the current paging info for this query.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  @Query(
      value =
          "select b from HostTallyBucket b join fetch b.host h where "
              + "h.orgId = :orgId and "
              + "b.key.productId = :product and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.billingProvider = :billingProvider and "
              + "b.key.billingAccountId = :billingAccountId and "
              +
              // Have to do the null check first, otherwise the lower in the LIKE clause has issues
              // with datatypes
              "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and "
              + "b.cores >= :minCores and b.sockets >= :minSockets",
      // Because we are using a 'fetch join' to avoid having to lazy load each bucket host,
      // we need to specify how the Page should gets its count when the 'limit' parameter
      // is used.
      countQuery =
          "select count(b) from HostTallyBucket b join b.host h where "
              + "h.orgId = :orgId and "
              + "b.key.productId = :product and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.sla = :sla and b.key.usage = :usage and "
              + "b.key.billingProvider = :billingProvider and "
              + "b.key.billingAccountId = :billingAccountId and "
              + "((lower(h.displayName) LIKE lower(concat('%', :displayNameSubstring,'%')))) and "
              + "b.cores >= :minCores and b.sockets >= :minSockets")
  Page<TallyHostView> getTallyHostViews(
      @Param("orgId") String orgId,
      @Param("product") String productId,
      @Param("sla") ServiceLevel sla,
      @Param("usage") Usage usage,
      @Param("billingProvider") BillingProvider billingProvider,
      @Param("billingAccountId") String billingAccountId,
      @NotNull @Param("displayNameSubstring") String displayNameSubstring,
      @Param("minCores") int minCores,
      @Param("minSockets") int minSockets,
      Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"buckets"})
  Page<Host> findAll(Specification<Host> specification, Pageable pageable);

  /**
   * Find all Hosts by bucket criteria and return a page of TallyHostView objects. A TallyHostView
   * is a Host representation detailing what 'bucket' was applied to the current daily snapshots.
   *
   * @param orgId The organization ID of the hosts to query (required).
   * @param productId The bucket product ID to filter Host by (pass null to ignore).
   * @param sla The bucket service level to filter Hosts by (pass null to ignore).
   * @param usage The bucket usage to filter Hosts by (pass null to ignore).
   * @param displayNameSubstring Case-insensitive string to filter Hosts' display name by (pass null
   *     or empty string to ignore)
   * @param minCores Filter to Hosts with at least this number of cores.
   * @param minSockets Filter to Hosts with at least this number of sockets.
   * @param month Filter to Hosts with with monthly instance totals in provided month
   * @param referenceUom Uom used when filtering to a specific month.
   * @param pageable the current paging info for this query.
   * @return a page of Host entities matching the criteria.
   */
  @SuppressWarnings("java:S107")
  default Page<Host> findAllBy(
      @Param("orgId") String orgId,
      @Param("product") String productId,
      @Param("sla") ServiceLevel sla,
      @Param("usage") Usage usage,
      @NotNull @Param("displayNameSubstring") String displayNameSubstring,
      @Param("minCores") int minCores,
      @Param("minSockets") int minSockets,
      String month,
      Uom referenceUom,
      BillingProvider billingProvider,
      String billingAccountId,
      List<HardwareMeasurementType> hardwareMeasurementTypes,
      Pageable pageable) {

    HostSpecification searchCriteria = new HostSpecification();

    searchCriteria.add(new SearchCriteria(Host_.ORG_ID, orgId, SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(HostBucketKey_.PRODUCT_ID, productId, SearchOperation.EQUAL));
    searchCriteria.add(new SearchCriteria(HostBucketKey_.SLA, sla, SearchOperation.EQUAL));
    searchCriteria.add(new SearchCriteria(HostBucketKey_.USAGE, usage, SearchOperation.EQUAL));

    searchCriteria.add(
        new SearchCriteria(
            HostBucketKey_.BILLING_PROVIDER, billingProvider, SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(
            HostBucketKey_.BILLING_ACCOUNT_ID, billingAccountId, SearchOperation.EQUAL));

    searchCriteria.add(
        new SearchCriteria(Host_.DISPLAY_NAME, displayNameSubstring, SearchOperation.CONTAINS));
    searchCriteria.add(
        new SearchCriteria(HostTallyBucket_.CORES, minCores, SearchOperation.GREATER_THAN_EQUAL));
    searchCriteria.add(
        new SearchCriteria(
            HostTallyBucket_.SOCKETS, minSockets, SearchOperation.GREATER_THAN_EQUAL));
    if (StringUtils.hasText(month)) {
      // Defaulting if null, since we need a UOM in order to properly filter against a given month
      Uom effectiveUom =
          Optional.ofNullable(referenceUom).orElse(getDefaultUomForProduct(productId));
      if (effectiveUom != null) {
        searchCriteria.add(
            new SearchCriteria(
                InstanceMonthlyTotalKey_.MONTH,
                new InstanceMonthlyTotalKey(month, effectiveUom),
                SearchOperation.EQUAL));
      }
    }
    if (Objects.nonNull(hardwareMeasurementTypes) && !hardwareMeasurementTypes.isEmpty()) {
      searchCriteria.add(
          new SearchCriteria(
              HostTallyBucket_.MEASUREMENT_TYPE, hardwareMeasurementTypes, SearchOperation.IN));
    }

    return findAll(searchCriteria, pageable);
  }

  default Uom getDefaultUomForProduct(String productId) {
    return Optional.ofNullable(getTagProfile().uomsForTag(productId)).orElse(List.of()).stream()
        .findFirst()
        .orElse(null);
  }

  @Query(
      "select distinct h from Host h where "
          + "h.orgId = :orgId and "
          + "h.hypervisorUuid = :hypervisor_id")
  Page<Host> getHostsByHypervisor(
      @Param("orgId") String orgId, @Param("hypervisor_id") String hypervisorId, Pageable pageable);

  List<Host> findByAccountNumber(String accountNumber);

  Optional<Host> findById(UUID id);

  void deleteByOrgId(String orgId);
}
