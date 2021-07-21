package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Account.class)
public abstract class Account_ {

	public static volatile SingularAttribute<Account, String> accountNumber;
	public static volatile MapAttribute<Account, String, Host> serviceInstances;

	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String SERVICE_INSTANCES = "serviceInstances";

}

