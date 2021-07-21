package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SubscriptionCapacityKey.class)
public abstract class SubscriptionCapacityKey_ {

	public static volatile SingularAttribute<SubscriptionCapacityKey, String> productId;
	public static volatile SingularAttribute<SubscriptionCapacityKey, String> ownerId;
	public static volatile SingularAttribute<SubscriptionCapacityKey, String> subscriptionId;

	public static final String PRODUCT_ID = "productId";
	public static final String OWNER_ID = "ownerId";
	public static final String SUBSCRIPTION_ID = "subscriptionId";

}

