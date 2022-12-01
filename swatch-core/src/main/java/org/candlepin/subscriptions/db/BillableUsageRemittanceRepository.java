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
import java.util.List;
import java.util.Objects;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK_;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity_;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BillableUsageRemittanceRepository
    extends JpaRepository<BillableUsageRemittanceEntity, BillableUsageRemittanceEntityPK>,
        JpaSpecificationExecutor<BillableUsageRemittanceEntity> {

  @Query
  void deleteByKeyOrgId(String orgId);

  default List<BillableUsageRemittanceEntity> filterBy(BillableUsageRemittanceFilter filter) {
    return this.findAll(buildSearchSpecification(filter));
  }

  static Specification<BillableUsageRemittanceEntity> matchingBillingProvider(
      String billingProvider) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(
          path.get(BillableUsageRemittanceEntityPK_.billingProvider), billingProvider);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingBillingAccountId(
      String billingAccountId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(
          path.get(BillableUsageRemittanceEntityPK_.billingAccountId), billingAccountId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingMetricId(String metricId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.metricId), metricId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingProductId(String productId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.productId), productId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingOrgId(String orgId) {
    return (root, query, builder) -> {
      var path = root.get(BillableUsageRemittanceEntity_.key);
      return builder.equal(path.get(BillableUsageRemittanceEntityPK_.orgId), orgId);
    };
  }

  static Specification<BillableUsageRemittanceEntity> matchingAccountNumber(String account) {
    return (root, query, builder) ->
        builder.equal(root.get(BillableUsageRemittanceEntity_.accountNumber), account);
  }

  static Specification<BillableUsageRemittanceEntity> beforeRemittanceDate(OffsetDateTime ending) {
    return (root, query, builder) ->
        builder.lessThanOrEqualTo(root.get(BillableUsageRemittanceEntity_.remittanceDate), ending);
  }

  static Specification<BillableUsageRemittanceEntity> afterRemittanceDate(
      OffsetDateTime beginning) {
    return (root, query, builder) ->
        builder.greaterThanOrEqualTo(
            root.get(BillableUsageRemittanceEntity_.remittanceDate), beginning);
  }

  default Specification<BillableUsageRemittanceEntity> buildSearchSpecification(
      BillableUsageRemittanceFilter filter) {

    var searchCriteria = Specification.<BillableUsageRemittanceEntity>where(null);
    if (Objects.nonNull(filter.getAccount())) {
      searchCriteria = searchCriteria.and(matchingAccountNumber(filter.getAccount()));
    }
    if (Objects.nonNull(filter.getProductId())) {
      searchCriteria = searchCriteria.and(matchingProductId(filter.getProductId()));
    }
    if (Objects.nonNull(filter.getMetricId())) {
      searchCriteria = searchCriteria.and(matchingMetricId(filter.getMetricId()));
    }
    if (Objects.nonNull(filter.getBillingProvider())) {
      searchCriteria = searchCriteria.and(matchingBillingProvider(filter.getBillingProvider()));
    }
    if (Objects.nonNull(filter.getBillingAccountId())) {
      searchCriteria = searchCriteria.and(matchingBillingAccountId(filter.getBillingAccountId()));
    }
    if (Objects.nonNull(filter.getOrgId())) {
      searchCriteria = searchCriteria.and(matchingOrgId(filter.getOrgId()));
    }
    if (Objects.nonNull(filter.getBeginning())) {
      searchCriteria = searchCriteria.and(afterRemittanceDate(filter.getBeginning()));
    }
    if (Objects.nonNull(filter.getEnding())) {
      searchCriteria = searchCriteria.and(beforeRemittanceDate(filter.getEnding()));
    }

    return searchCriteria;
  }
}
