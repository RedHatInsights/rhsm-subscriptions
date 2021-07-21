package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(EventRecord.class)
public abstract class EventRecord_ {

	public static volatile SingularAttribute<EventRecord, String> instanceId;
	public static volatile SingularAttribute<EventRecord, String> eventSource;
	public static volatile SingularAttribute<EventRecord, UUID> id;
	public static volatile SingularAttribute<EventRecord, String> eventType;
	public static volatile SingularAttribute<EventRecord, String> accountNumber;
	public static volatile SingularAttribute<EventRecord, OffsetDateTime> timestamp;

	public static final String INSTANCE_ID = "instanceId";
	public static final String EVENT_SOURCE = "eventSource";
	public static final String ID = "id";
	public static final String EVENT_TYPE = "eventType";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String TIMESTAMP = "timestamp";

}

