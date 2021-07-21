package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(TallySnapshot.class)
public abstract class TallySnapshot_ {

	public static volatile SingularAttribute<TallySnapshot, OffsetDateTime> snapshotDate;
	public static volatile MapAttribute<TallySnapshot, TallyMeasurementKey, Double> tallyMeasurements;
	public static volatile SingularAttribute<TallySnapshot, String> productId;
	public static volatile MapAttribute<TallySnapshot, HardwareMeasurementType, HardwareMeasurement> hardwareMeasurements;
	public static volatile SingularAttribute<TallySnapshot, Granularity> granularity;
	public static volatile SingularAttribute<TallySnapshot, Usage> usage;
	public static volatile SingularAttribute<TallySnapshot, UUID> id;
	public static volatile SingularAttribute<TallySnapshot, String> ownerId;
	public static volatile SingularAttribute<TallySnapshot, String> accountNumber;
	public static volatile SingularAttribute<TallySnapshot, ServiceLevel> serviceLevel;

	public static final String SNAPSHOT_DATE = "snapshotDate";
	public static final String TALLY_MEASUREMENTS = "tallyMeasurements";
	public static final String PRODUCT_ID = "productId";
	public static final String HARDWARE_MEASUREMENTS = "hardwareMeasurements";
	public static final String GRANULARITY = "granularity";
	public static final String USAGE = "usage";
	public static final String ID = "id";
	public static final String OWNER_ID = "ownerId";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String SERVICE_LEVEL = "serviceLevel";

}

