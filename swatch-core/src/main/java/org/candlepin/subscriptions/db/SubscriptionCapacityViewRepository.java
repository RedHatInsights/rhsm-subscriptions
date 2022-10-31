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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionCapacityViewRepository
    extends JpaRepository<SubscriptionCapacityView, SubscriptionCapacityKey>,
        JpaSpecificationExecutor<SubscriptionCapacityView> {

  default List<SubscriptionCapacityView> findAllBy(
      String orgId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd,
      Uom uom) {

    return findAll(
        SubscriptionCapacityViewSpecification.builder()
            .criteria(
                buildSearchCriteria(
                    orgId, productId, serviceLevel, usage, reportStart, reportEnd, uom))
            .build());
  }

  private List<SearchCriteria> defaultSearchCriteria(String orgId, String productId) {
    return new ArrayList<>(
        List.of(
            SearchCriteria.builder()
                .key(SubscriptionCapacityKey_.orgId.getName())
                .operation(SearchOperation.EQUAL)
                .value(orgId)
                .build(),
            SearchCriteria.builder()
                .key(SubscriptionCapacityKey_.productId.getName())
                .operation(SearchOperation.EQUAL)
                .value(productId)
                .build()));
  }

  private List<SearchCriteria> searchCriteriaForReportDuration(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return List.of(
        SearchCriteria.builder()
            .key(SubscriptionCapacityView_.beginDate.getName())
            .operation(SearchOperation.BEFORE_OR_ON)
            .value(reportEnd)
            .build(),
        SearchCriteria.builder()
            .key(SubscriptionCapacityView_.endDate.getName())
            .operation(SearchOperation.AFTER_OR_ON)
            .value(reportStart)
            .build());
  }

  private SearchCriteria searchCriteriaMatchingSLA(ServiceLevel serviceLevel) {
    return SearchCriteria.builder()
        .key(SubscriptionCapacityView_.serviceLevel.getName())
        .operation(SearchOperation.EQUAL)
        .value(serviceLevel)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingUsage(Usage usage) {
    return SearchCriteria.builder()
        .key(SubscriptionCapacityView_.usage.getName())
        .operation(SearchOperation.EQUAL)
        .value(usage)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingUomOfCores() {
    return SearchCriteria.builder()
        .key(SubscriptionCapacityView_.physicalCores.getName())
        .operation(SearchOperation.IS_NOT_NULL)
        .build();
  }

  private SearchCriteria searchCriteriaMatchingUomOfSockets() {
    return SearchCriteria.builder()
        .key(SubscriptionCapacityView_.physicalSockets.getName())
        .operation(SearchOperation.IS_NOT_NULL)
        .build();
  }

  private List<SearchCriteria> buildSearchCriteria(
      String orgId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd,
      Uom uom) {

    List<SearchCriteria> searchCriteria = defaultSearchCriteria(orgId, productId);
    if (Uom.CORES.equals(uom)) searchCriteria.add(searchCriteriaMatchingUomOfCores());
    if (Uom.SOCKETS.equals(uom)) searchCriteria.add(searchCriteriaMatchingUomOfSockets());
    if (Objects.nonNull(serviceLevel) && !serviceLevel.equals(ServiceLevel._ANY))
      searchCriteria.add(searchCriteriaMatchingSLA(serviceLevel));
    if (Objects.nonNull(usage) && !usage.equals(Usage._ANY))
      searchCriteria.add(searchCriteriaMatchingUsage(usage));
    searchCriteria.addAll(searchCriteriaForReportDuration(reportStart, reportEnd));
    return searchCriteria;
  }
}
