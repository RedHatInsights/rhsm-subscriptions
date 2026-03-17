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

import jakarta.persistence.criteria.JoinType;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.jpa.domain.Specification;

/** Specifications for building dynamic queries on TallySnapshot.
 *  Methods created with assistance from Claude Code
 * */
public class TallySnapshotSpecifications {

  private TallySnapshotSpecifications() {
    // Utility class
  }

  public static Specification<TallySnapshot> isPrimary(Boolean isPrimary) {
    return (root, query, cb) ->
        isPrimary == null ? null : cb.equal(root.get("isPrimary"), isPrimary);
  }

  public static Specification<TallySnapshot> hasOrgId(String orgId) {
    return (root, query, cb) -> orgId == null ? null : cb.equal(root.get("orgId"), orgId);
  }

  public static Specification<TallySnapshot> hasProductId(String productId) {
    return (root, query, cb) ->
        productId == null ? null : cb.equal(root.get("productId"), productId);
  }

  public static Specification<TallySnapshot> hasGranularity(Granularity granularity) {
    return (root, query, cb) ->
        granularity == null ? null : cb.equal(root.get("granularity"), granularity);
  }

  public static Specification<TallySnapshot> hasServiceLevel(ServiceLevel serviceLevel) {
    return (root, query, cb) ->
        serviceLevel == null || ServiceLevel._ANY.equals(serviceLevel)
            ? null
            : cb.equal(root.get("serviceLevel"), serviceLevel);
  }

  public static Specification<TallySnapshot> hasUsage(Usage usage) {
    return (root, query, cb) ->
        usage == null || Usage._ANY.equals(usage) ? null : cb.equal(root.get("usage"), usage);
  }

  public static Specification<TallySnapshot> hasBillingProvider(BillingProvider billingProvider) {
    return (root, query, cb) ->
        billingProvider == null || BillingProvider._ANY.equals(billingProvider)
            ? null
            : cb.equal(root.get("billingProvider"), billingProvider);
  }

  public static Specification<TallySnapshot> hasBillingAccountId(String billingAccountId) {
    return (root, query, cb) ->
        billingAccountId == null || "_ANY".equals(billingAccountId)
            ? null
            : cb.equal(root.get("billingAccountId"), billingAccountId);
  }

  public static Specification<TallySnapshot> snapshotDateBetween(
      OffsetDateTime beginning, OffsetDateTime ending) {
    return (root, query, cb) -> {
      if (beginning == null && ending == null) {
        return null;
      }
      if (beginning == null) {
        return cb.lessThanOrEqualTo(root.get("snapshotDate"), ending);
      }
      if (ending == null) {
        return cb.greaterThanOrEqualTo(root.get("snapshotDate"), beginning);
      }
      return cb.between(root.get("snapshotDate"), beginning, ending);
    };
  }

  public static Specification<TallySnapshot> withTallyMeasurements() {
    return (root, query, cb) -> {
      // Fetch join for tallyMeasurements to avoid N+1 queries
      // Only apply this for select queries, not count queries
      if (query.getResultType() == TallySnapshot.class) {
        root.fetch("tallyMeasurements", JoinType.LEFT);
        query.distinct(true);
      }
      return null;
    };
  }

  public static Specification<TallySnapshot> orderBySnapshotDate() {
    return (root, query, cb) -> {
      // Only apply ordering for select queries, not count queries
      if (query.getResultType() == TallySnapshot.class) {
        query.orderBy(cb.asc(root.get("snapshotDate")));
      }
      return null;
    };
  }

  public static Specification<TallySnapshot> buildSnapshotSearchSpec(
      Boolean isPrimary,
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    return Specification.where(isPrimary(isPrimary))
        .and(hasOrgId(orgId))
        .and(hasProductId(productId))
        .and(hasGranularity(granularity))
        .and(hasServiceLevel(serviceLevel))
        .and(hasUsage(usage))
        .and(hasBillingProvider(billingProvider))
        .and(hasBillingAccountId(billingAccountId))
        .and(snapshotDateBetween(beginning, ending))
        .and(withTallyMeasurements())
        .and(orderBySnapshotDate());
  }
}
