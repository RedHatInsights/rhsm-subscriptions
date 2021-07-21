package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(HostTallyBucket.class)
public abstract class HostTallyBucket_ {

	public static volatile SingularAttribute<HostTallyBucket, Integer> cores;
	public static volatile SingularAttribute<HostTallyBucket, HardwareMeasurementType> measurementType;
	public static volatile SingularAttribute<HostTallyBucket, Host> host;
	public static volatile SingularAttribute<HostTallyBucket, Integer> sockets;
	public static volatile SingularAttribute<HostTallyBucket, HostBucketKey> key;

	public static final String CORES = "cores";
	public static final String MEASUREMENT_TYPE = "measurementType";
	public static final String HOST = "host";
	public static final String SOCKETS = "sockets";
	public static final String KEY = "key";

}

