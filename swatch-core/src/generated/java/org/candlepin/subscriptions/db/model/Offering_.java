package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Offering.class)
public abstract class Offering_ {

	public static volatile SingularAttribute<Offering, String> productFamily;
	public static volatile SingularAttribute<Offering, Integer> physicalSockets;
	public static volatile SetAttribute<Offering, String> childSkus;
	public static volatile SingularAttribute<Offering, Integer> virtualCores;
	public static volatile SingularAttribute<Offering, Integer> virtualSockets;
	public static volatile SingularAttribute<Offering, String> role;
	public static volatile SetAttribute<Offering, Integer> productIds;
	public static volatile SingularAttribute<Offering, Usage> usage;
	public static volatile SingularAttribute<Offering, Integer> physicalCores;
	public static volatile SingularAttribute<Offering, String> sku;
	public static volatile SingularAttribute<Offering, ServiceLevel> serviceLevel;
	public static volatile SingularAttribute<Offering, String> productName;

	public static final String PRODUCT_FAMILY = "productFamily";
	public static final String PHYSICAL_SOCKETS = "physicalSockets";
	public static final String CHILD_SKUS = "childSkus";
	public static final String VIRTUAL_CORES = "virtualCores";
	public static final String VIRTUAL_SOCKETS = "virtualSockets";
	public static final String ROLE = "role";
	public static final String PRODUCT_IDS = "productIds";
	public static final String USAGE = "usage";
	public static final String PHYSICAL_CORES = "physicalCores";
	public static final String SKU = "sku";
	public static final String SERVICE_LEVEL = "serviceLevel";
	public static final String PRODUCT_NAME = "productName";

}

