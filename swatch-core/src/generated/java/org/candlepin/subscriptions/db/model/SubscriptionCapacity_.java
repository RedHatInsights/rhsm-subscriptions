package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SubscriptionCapacity.class)
public abstract class SubscriptionCapacity_ {

	public static volatile SingularAttribute<SubscriptionCapacity, Integer> physicalSockets;
	public static volatile SingularAttribute<SubscriptionCapacity, OffsetDateTime> beginDate;
	public static volatile SingularAttribute<SubscriptionCapacity, Integer> virtualSockets;
	public static volatile SingularAttribute<SubscriptionCapacity, Integer> virtualCores;
	public static volatile SingularAttribute<SubscriptionCapacity, Boolean> hasUnlimitedGuestSockets;
	public static volatile SingularAttribute<SubscriptionCapacity, OffsetDateTime> endDate;
	public static volatile SingularAttribute<SubscriptionCapacity, Usage> usage;
	public static volatile SingularAttribute<SubscriptionCapacity, Integer> physicalCores;
	public static volatile SingularAttribute<SubscriptionCapacity, String> accountNumber;
	public static volatile SingularAttribute<SubscriptionCapacity, String> sku;
	public static volatile SingularAttribute<SubscriptionCapacity, ServiceLevel> serviceLevel;
	public static volatile SingularAttribute<SubscriptionCapacity, SubscriptionCapacityKey> key;

	public static final String PHYSICAL_SOCKETS = "physicalSockets";
	public static final String BEGIN_DATE = "beginDate";
	public static final String VIRTUAL_SOCKETS = "virtualSockets";
	public static final String VIRTUAL_CORES = "virtualCores";
	public static final String HAS_UNLIMITED_GUEST_SOCKETS = "hasUnlimitedGuestSockets";
	public static final String END_DATE = "endDate";
	public static final String USAGE = "usage";
	public static final String PHYSICAL_CORES = "physicalCores";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String SKU = "sku";
	public static final String SERVICE_LEVEL = "serviceLevel";
	public static final String KEY = "key";

}

