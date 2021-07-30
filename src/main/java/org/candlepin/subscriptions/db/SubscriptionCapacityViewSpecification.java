package org.candlepin.subscriptions.db;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;

@Builder
@AllArgsConstructor(access= AccessLevel.PRIVATE)
public class SubscriptionCapacityViewSpecification implements Specification<SubscriptionCapacityView> {

    private transient SearchCriteria criteria;

    @Override
    public Predicate toPredicate
            (Root<SubscriptionCapacityView> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

        Path expression = null;
        if ("ownerId".equals(criteria.getKey())) {
            expression = root.get("key").get("ownerId");
        } else if ("subscriptionId".equals(criteria.getKey())) {
            expression = root.get("key").get("subscriptionId");
        }else if("productId".equals(criteria.getKey())){
            expression = root.get("key").get("productId");
        } else {
            expression = root.get(criteria.getKey());
        }

        if (criteria.getOperation().equals(SearchOperation.GREATER_THAN_EQUAL)) {
            return builder.greaterThanOrEqualTo(expression, criteria.getValue().toString());
        }
        else if (criteria.getOperation().equals(SearchOperation.LESS_THAN_EQUAL)) {
            return builder.lessThanOrEqualTo(expression, criteria.getValue().toString());
        }
        else if (criteria.getOperation().equals(SearchOperation.EQUAL)) {
            return builder.equal(expression, criteria.getValue());
        }
        return null;
    }

}
