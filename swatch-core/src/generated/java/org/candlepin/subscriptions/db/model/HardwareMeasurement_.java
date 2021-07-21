package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(HardwareMeasurement.class)
public abstract class HardwareMeasurement_ {

	public static volatile SingularAttribute<HardwareMeasurement, Integer> cores;
	public static volatile SingularAttribute<HardwareMeasurement, Integer> instanceCount;
	public static volatile SingularAttribute<HardwareMeasurement, Integer> sockets;

	public static final String CORES = "cores";
	public static final String INSTANCE_COUNT = "instanceCount";
	public static final String SOCKETS = "sockets";

}

