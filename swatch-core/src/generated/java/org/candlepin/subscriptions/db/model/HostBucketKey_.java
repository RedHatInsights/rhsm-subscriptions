package org.candlepin.subscriptions.db.model;

import java.util.UUID;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(HostBucketKey.class)
public abstract class HostBucketKey_ {

	public static volatile SingularAttribute<HostBucketKey, String> productId;
	public static volatile SingularAttribute<HostBucketKey, Usage> usage;
	public static volatile SingularAttribute<HostBucketKey, Boolean> asHypervisor;
	public static volatile SingularAttribute<HostBucketKey, UUID> hostId;
	public static volatile SingularAttribute<HostBucketKey, ServiceLevel> sla;

	public static final String PRODUCT_ID = "productId";
	public static final String USAGE = "usage";
	public static final String AS_HYPERVISOR = "asHypervisor";
	public static final String HOST_ID = "hostId";
	public static final String SLA = "sla";

}

