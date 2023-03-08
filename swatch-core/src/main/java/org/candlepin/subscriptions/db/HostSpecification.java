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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey_;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.HostTallyBucket_;
import org.candlepin.subscriptions.db.model.Host_;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey_;
import org.springframework.data.jpa.domain.Specification;

/** Util class for dynamically building Specification&lt;Host&gt; */
public class HostSpecification implements Specification<Host> {

  private final transient List<SearchCriteria> list;

  public HostSpecification() {
    this.list = new ArrayList<>();
  }

  public void add(SearchCriteria criteria) {
    list.add(criteria);
  }

  @Override
  @SuppressWarnings("java:S3776")
  public Predicate toPredicate(Root<Host> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

    Join<Host, HostTallyBucket> bucketListJoin = root.join(Host_.buckets, JoinType.INNER);
    MapJoin<Host, InstanceMonthlyTotalKey, Double> instanceMonthlyTotalRoot =
        root.joinMap(Host_.MONTHLY_TOTALS, JoinType.LEFT);

    List<Predicate> predicates = new ArrayList<>();

    Map<String, Path<?>> rootPaths =
        Map.of(
            HostBucketKey_.PRODUCT_ID, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.SLA, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.USAGE, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.BILLING_ACCOUNT_ID, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.BILLING_PROVIDER, bucketListJoin.get(HostTallyBucket_.KEY),
            HostTallyBucket_.CORES, bucketListJoin,
            HostTallyBucket_.SOCKETS, bucketListJoin,
            HostTallyBucket_.MEASUREMENT_TYPE, bucketListJoin,
            InstanceMonthlyTotalKey_.MONTH, instanceMonthlyTotalRoot);

    for (SearchCriteria criteria : list) {
      Path<?> path = rootPaths.getOrDefault(criteria.getKey(), root);
      if (criteria.getOperation().equals(SearchOperation.GREATER_THAN)) {
        predicates.add(
            builder.greaterThan(path.get(criteria.getKey()), criteria.getValue().toString()));
      } else if (criteria.getOperation().equals(SearchOperation.LESS_THAN)) {
        predicates.add(
            builder.lessThan(path.get(criteria.getKey()), criteria.getValue().toString()));
      } else if (criteria.getOperation().equals(SearchOperation.GREATER_THAN_EQUAL)) {
        predicates.add(
            builder.greaterThanOrEqualTo(
                path.get(criteria.getKey()), criteria.getValue().toString()));
      } else if (criteria.getOperation().equals(SearchOperation.LESS_THAN_EQUAL)) {
        predicates.add(
            builder.lessThanOrEqualTo(path.get(criteria.getKey()), criteria.getValue().toString()));
      } else if (criteria.getOperation().equals(SearchOperation.NOT_EQUAL)) {
        predicates.add(builder.notEqual(path.get(criteria.getKey()), criteria.getValue()));
      } else if (criteria.getOperation().equals(SearchOperation.EQUAL)) {
        var key =
            Objects.equals(instanceMonthlyTotalRoot, path)
                ? instanceMonthlyTotalRoot.key()
                : path.get(criteria.getKey());

        predicates.add(builder.equal(key, criteria.getValue()));

      } else if (criteria.getOperation().equals(SearchOperation.CONTAINS)) {
        predicates.add(
            builder.like(
                builder.lower(path.get(criteria.getKey())),
                "%" + criteria.getValue().toString().toLowerCase() + "%"));
      } else if (criteria.getOperation().equals(SearchOperation.MATCH_END)) {
        predicates.add(
            builder.like(
                builder.lower(path.get(criteria.getKey())),
                criteria.getValue().toString().toLowerCase() + "%"));
      } else if (criteria.getOperation().equals(SearchOperation.MATCH_START)) {
        predicates.add(
            builder.like(
                builder.lower(path.get(criteria.getKey())),
                "%" + criteria.getValue().toString().toLowerCase()));
      } else if (criteria.getOperation().equals(SearchOperation.IN)) {
        predicates.add(builder.in(path.get(criteria.getKey())).value(criteria.getValue()));
      } else if (criteria.getOperation().equals(SearchOperation.NOT_IN)) {
        predicates.add(builder.not(path.get(criteria.getKey())).in(criteria.getValue()));
      }
    }

    return builder.and(predicates.toArray(new Predicate[0]));
  }
}
