package org.candlepin.subscriptions.db;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Builder
@AllArgsConstructor(access= AccessLevel.PRIVATE)
public class SubscriptionCapacityViewSpecification implements Specification<SubscriptionCapacityView> {

    private final transient List<SearchCriteria> criteria;

    @Override
    public Predicate toPredicate
            (Root<SubscriptionCapacityView> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

        return builder.and(
            criteria.stream()
                    .map(c -> mapSingleSearchCriteriaToPredicate(root, c, builder))
                    .filter(Objects::nonNull).peek(c -> System.out.println("Predicate returned: "+c.toString()))
                .toArray(Predicate[]::new));
    }

    private Predicate mapSingleSearchCriteriaToPredicate(
            Root<SubscriptionCapacityView> root,
            SearchCriteria criteria,
            CriteriaBuilder builder){

        Path expression = null;
        if ("ownerId".equals(criteria.getKey())) {
            expression = root.get("key").get("ownerId");
        } else if ("subscriptionId".equals(criteria.getKey())) {
            expression = root.get("key").get("subscriptionId");
        }else if("productId".equals(criteria.getKey())){
            expression = root.get("key").get("productId");
        }  else {
            expression = root.get(criteria.getKey());
        }

        if (criteria.getOperation().equals(SearchOperation.GREATER_THAN_EQUAL)) {
            return builder.greaterThanOrEqualTo(expression, criteria.getValue().toString());
        } else if (criteria.getOperation().equals(SearchOperation.LESS_THAN_EQUAL)) {
            return builder.lessThanOrEqualTo(expression, criteria.getValue().toString());
        } else if (criteria.getOperation().equals(SearchOperation.AFTER_OR_ON)) {
            return builder.greaterThanOrEqualTo(expression, (OffsetDateTime)criteria.getValue());
        } else if (criteria.getOperation().equals(SearchOperation.BEFORE_OR_ON)) {
            return builder.lessThanOrEqualTo(expression, (OffsetDateTime)criteria.getValue());
        } else {
            return builder.equal(expression, criteria.getValue());
        }
    }

}
