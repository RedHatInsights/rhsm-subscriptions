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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionCapacityViewRepository
    extends JpaRepository<SubscriptionCapacityView, SubscriptionCapacityKey>,
        JpaSpecificationExecutor<SubscriptionCapacityView> {

  default List<SubscriptionCapacityView> findAllBy(
      String ownerId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {
    return findAll(
        SubscriptionCapacityViewSpecification.builder()
            .criteria(
                buildSearchCriteria(
                    ownerId, productId, serviceLevel, usage, reportStart, reportEnd))
            .build());
  }

  private List<SearchCriteria> defaultSearchCriteria(String ownerId, String productId) {
    return new ArrayList<>(
        List.of(
            SearchCriteria.builder()
                .key(SubscriptionCapacityKey_.ownerId.getName())
                .operation(SearchOperation.EQUAL)
                .value(ownerId)
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

  private List<SearchCriteria> buildSearchCriteria(
      String ownerId,
      String productId,
      ServiceLevel serviceLevel,
      Usage usage,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {

    List<SearchCriteria> searchCriteria = defaultSearchCriteria(ownerId, productId);
    if (Objects.nonNull(serviceLevel)) searchCriteria.add(searchCriteriaMatchingSLA(serviceLevel));
    if (Objects.nonNull(usage)) searchCriteria.add(searchCriteriaMatchingUsage(usage));
    searchCriteria.addAll(searchCriteriaForReportDuration(reportStart, reportEnd));
    return searchCriteria;
  }
}
