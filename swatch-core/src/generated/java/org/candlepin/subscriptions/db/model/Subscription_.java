package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Subscription.class)
public abstract class Subscription_ {

	public static volatile SingularAttribute<Subscription, Long> quantity;
	public static volatile SingularAttribute<Subscription, String> subscriptionNumber;
	public static volatile SingularAttribute<Subscription, OffsetDateTime> endDate;
	public static volatile SingularAttribute<Subscription, String> marketplaceSubscriptionId;
	public static volatile SingularAttribute<Subscription, String> subscriptionId;
	public static volatile SingularAttribute<Subscription, String> sku;
	public static volatile SingularAttribute<Subscription, String> ownerId;
	public static volatile SingularAttribute<Subscription, String> accountNumber;
	public static volatile SingularAttribute<Subscription, OffsetDateTime> startDate;

	public static final String QUANTITY = "quantity";
	public static final String SUBSCRIPTION_NUMBER = "subscriptionNumber";
	public static final String END_DATE = "endDate";
	public static final String MARKETPLACE_SUBSCRIPTION_ID = "marketplaceSubscriptionId";
	public static final String SUBSCRIPTION_ID = "subscriptionId";
	public static final String SKU = "sku";
	public static final String OWNER_ID = "ownerId";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String START_DATE = "startDate";

}

