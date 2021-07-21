package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.subscriptions.json.Measurement.Uom;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(InstanceMonthlyTotalKey.class)
public abstract class InstanceMonthlyTotalKey_ {

	public static volatile SingularAttribute<InstanceMonthlyTotalKey, Uom> uom;
	public static volatile SingularAttribute<InstanceMonthlyTotalKey, String> month;

	public static final String UOM = "uom";
	public static final String MONTH = "month";

}

