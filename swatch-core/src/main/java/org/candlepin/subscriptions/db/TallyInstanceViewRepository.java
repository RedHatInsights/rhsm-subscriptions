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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey_;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstanceViewKey_;
import org.candlepin.subscriptions.db.model.TallyInstanceView_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

/** Provides access to TallyInstanceView database entities. */
@SuppressWarnings({"linelength", "indentation"})
public interface TallyInstanceViewRepository
    extends JpaRepository<TallyInstanceView, UUID>,
        JpaSpecificationExecutor<TallyInstanceView>,
        TagProfileLookup {

  @Override
  Page<TallyInstanceView> findAll(
      Specification<TallyInstanceView> specification, Pageable pageable);

  /**
   * Find all Hosts by bucket criteria and return a page of TallyInstanceView objects. A
   * TallyInstanceView is a Host representation detailing what 'bucket' was applied to the current
   * daily snapshots.
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
  default Page<TallyInstanceView> findAllBy(
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

    TallyInstanceViewSpecification searchCriteria = new TallyInstanceViewSpecification();

    Uom effectiveUom = Optional.ofNullable(referenceUom).orElse(getDefaultUomForProduct(productId));

    searchCriteria.add(new SearchCriteria(TallyInstanceView_.ORG_ID, orgId, SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(TallyInstanceViewKey_.PRODUCT_ID, productId, SearchOperation.EQUAL));
    searchCriteria.add(new SearchCriteria(TallyInstanceViewKey_.SLA, sla, SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(TallyInstanceViewKey_.USAGE, usage, SearchOperation.EQUAL));

    searchCriteria.add(
        new SearchCriteria(
            TallyInstanceViewKey_.BUCKET_BILLING_PROVIDER, billingProvider, SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(
            TallyInstanceViewKey_.BUCKET_BILLING_ACCOUNT_ID,
            billingAccountId,
            SearchOperation.EQUAL));
    searchCriteria.add(
        new SearchCriteria(
            TallyInstanceView_.DISPLAY_NAME, displayNameSubstring, SearchOperation.CONTAINS));
    if (effectiveUom != null) {
      searchCriteria.add(
          new SearchCriteria(TallyInstanceView_.UOM, effectiveUom, SearchOperation.EQUAL));
    }
    searchCriteria.add(
        new SearchCriteria(TallyInstanceView_.CORES, minCores, SearchOperation.GREATER_THAN_EQUAL));
    searchCriteria.add(
        new SearchCriteria(
            TallyInstanceView_.SOCKETS, minSockets, SearchOperation.GREATER_THAN_EQUAL));

    if (StringUtils.hasText(month) && effectiveUom != null) {
      searchCriteria.add(
          new SearchCriteria(
              InstanceMonthlyTotalKey_.MONTH,
              new InstanceMonthlyTotalKey(month, effectiveUom),
              SearchOperation.EQUAL));
    }
    if (Objects.nonNull(hardwareMeasurementTypes) && !hardwareMeasurementTypes.isEmpty()) {
      searchCriteria.add(
          new SearchCriteria(
              TallyInstanceViewKey_.MEASUREMENT_TYPE,
              hardwareMeasurementTypes,
              SearchOperation.IN));
    }

    return findAll(searchCriteria, pageable);
  }

  default Uom getDefaultUomForProduct(String productId) {
    return Optional.ofNullable(getTagProfile().uomsForTag(productId)).orElse(List.of()).stream()
        .findFirst()
        .orElse(null);
  }
}
