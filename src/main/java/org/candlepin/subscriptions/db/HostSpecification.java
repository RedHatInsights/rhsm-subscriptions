/*
 * Copyright (c) 2021 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.HostTallyBucket_;
import org.candlepin.subscriptions.db.model.Host_;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Util class for dynamically building Specification<Host>
 */
public class HostSpecification implements Specification<Host> {

    private List<SearchCriteria> list;

    public HostSpecification() {
        this.list = new ArrayList<>();
    }

    public void add(SearchCriteria criteria) {
        list.add(criteria);
    }

    @Override
    public Predicate toPredicate(Root<Host> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

        ListJoin<Host, HostTallyBucket> bucketListJoin = root.join(Host_.buckets, JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();

        Map<String, Path<?>> rootPaths = Map
            .of(
            HostBucketKey_.PRODUCT_ID, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.SLA, bucketListJoin.get(HostTallyBucket_.KEY),
            HostBucketKey_.USAGE, bucketListJoin.get(HostTallyBucket_.KEY),
            HostTallyBucket_.CORES, bucketListJoin,
            HostTallyBucket_.SOCKETS, bucketListJoin,
            HostTallyBucket_.MEASUREMENT_TYPE, bucketListJoin
        );

        for (SearchCriteria criteria : list) {
            Path<?> path = rootPaths.getOrDefault(criteria.getKey(), root);
            if (criteria.getOperation().equals(SearchOperation.GREATER_THAN)) {
                predicates
                    .add(builder.greaterThan(path.get(criteria.getKey()), criteria.getValue().toString()));
            }
            else if (criteria.getOperation().equals(SearchOperation.LESS_THAN)) {
                predicates.add(builder.lessThan(path.get(criteria.getKey()), criteria.getValue().toString()));
            }
            else if (criteria.getOperation().equals(SearchOperation.GREATER_THAN_EQUAL)) {
                predicates.add(builder
                    .greaterThanOrEqualTo(path.get(criteria.getKey()), criteria.getValue().toString()));
            }
            else if (criteria.getOperation().equals(SearchOperation.LESS_THAN_EQUAL)) {
                predicates.add(
                    builder.lessThanOrEqualTo(path.get(criteria.getKey()), criteria.getValue().toString()));
            }
            else if (criteria.getOperation().equals(SearchOperation.NOT_EQUAL)) {
                predicates.add(builder.notEqual(path.get(criteria.getKey()), criteria.getValue()));
            }
            else if (criteria.getOperation().equals(SearchOperation.EQUAL)) {
                predicates.add(builder.equal(path.get(criteria.getKey()), criteria.getValue()));
            }
            else if (criteria.getOperation().equals(SearchOperation.CONTAINS)) {
                predicates.add(builder.like(builder.lower(path.get(criteria.getKey())),
                    "%" + criteria.getValue().toString().toLowerCase() + "%"));
            }
            else if (criteria.getOperation().equals(SearchOperation.MATCH_END)) {
                predicates.add(builder.like(builder.lower(path.get(criteria.getKey())),
                    criteria.getValue().toString().toLowerCase() + "%"));
            }
            else if (criteria.getOperation().equals(SearchOperation.MATCH_START)) {
                predicates.add(builder.like(builder.lower(path.get(criteria.getKey())),
                    "%" + criteria.getValue().toString().toLowerCase()));
            }
            else if (criteria.getOperation().equals(SearchOperation.IN)) {
                predicates.add(builder.in(path.get(criteria.getKey())).value(criteria.getValue()));
            }
            else if (criteria.getOperation().equals(SearchOperation.NOT_IN)) {
                predicates.add(builder.not(path.get(criteria.getKey())).in(criteria.getValue()));
            }
        }

        //Helps omit duplicate rows when there is more than one bucket per host (like with RHEL
        // hypervisors, ENT-3210)
        Predicate nonHypervisor = builder.isFalse(root.get(Host_.IS_HYPERVISOR));
        Predicate isHypervisor = builder.isTrue(root.get(Host_.IS_HYPERVISOR));
        Predicate asHypervisor = builder
            .isTrue(bucketListJoin.get(HostTallyBucket_.KEY).get(HostBucketKey_.AS_HYPERVISOR));
        Predicate predicateForHypervisor = builder.and(isHypervisor, asHypervisor);

        predicates.add(builder.or(nonHypervisor, predicateForHypervisor));

        return builder.and(predicates.toArray(new Predicate[0]));
    }
}

