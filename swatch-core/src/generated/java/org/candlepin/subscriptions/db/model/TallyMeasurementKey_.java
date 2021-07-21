package org.candlepin.subscriptions.db.model;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.subscriptions.json.Measurement.Uom;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(TallyMeasurementKey.class)
public abstract class TallyMeasurementKey_ {

	public static volatile SingularAttribute<TallyMeasurementKey, Uom> uom;
	public static volatile SingularAttribute<TallyMeasurementKey, HardwareMeasurementType> measurementType;

	public static final String UOM = "uom";
	public static final String MEASUREMENT_TYPE = "measurementType";

}

