package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.annotation.Generated;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.candlepin.subscriptions.json.Measurement.Uom;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Host.class)
public abstract class Host_ {

	public static volatile SingularAttribute<Host, Integer> numOfGuests;
	public static volatile SingularAttribute<Host, String> displayName;
	public static volatile SetAttribute<Host, HostTallyBucket> buckets;
	public static volatile SingularAttribute<Host, String> instanceType;
	public static volatile SingularAttribute<Host, HostHardwareType> hardwareType;
	public static volatile SingularAttribute<Host, String> hypervisorUuid;
	public static volatile SingularAttribute<Host, Boolean> isHypervisor;
	public static volatile SingularAttribute<Host, String> accountNumber;
	public static volatile SingularAttribute<Host, String> orgId;
	public static volatile SingularAttribute<Host, String> subscriptionManagerId;
	public static volatile SingularAttribute<Host, String> instanceId;
	public static volatile SingularAttribute<Host, Integer> cores;
	public static volatile SingularAttribute<Host, OffsetDateTime> lastSeen;
	public static volatile SingularAttribute<Host, Boolean> isUnmappedGuest;
	public static volatile SingularAttribute<Host, String> cloudProvider;
	public static volatile SingularAttribute<Host, String> inventoryId;
	public static volatile MapAttribute<Host, InstanceMonthlyTotalKey, Double> monthlyTotals;
	public static volatile SingularAttribute<Host, String> insightsId;
	public static volatile SingularAttribute<Host, Boolean> guest;
	public static volatile SingularAttribute<Host, UUID> id;
	public static volatile SingularAttribute<Host, Integer> sockets;
	public static volatile MapAttribute<Host, Uom, Double> measurements;

	public static final String NUM_OF_GUESTS = "numOfGuests";
	public static final String DISPLAY_NAME = "displayName";
	public static final String BUCKETS = "buckets";
	public static final String INSTANCE_TYPE = "instanceType";
	public static final String HARDWARE_TYPE = "hardwareType";
	public static final String HYPERVISOR_UUID = "hypervisorUuid";
	public static final String IS_HYPERVISOR = "isHypervisor";
	public static final String ACCOUNT_NUMBER = "accountNumber";
	public static final String ORG_ID = "orgId";
	public static final String SUBSCRIPTION_MANAGER_ID = "subscriptionManagerId";
	public static final String INSTANCE_ID = "instanceId";
	public static final String CORES = "cores";
	public static final String LAST_SEEN = "lastSeen";
	public static final String IS_UNMAPPED_GUEST = "isUnmappedGuest";
	public static final String CLOUD_PROVIDER = "cloudProvider";
	public static final String INVENTORY_ID = "inventoryId";
	public static final String MONTHLY_TOTALS = "monthlyTotals";
	public static final String INSIGHTS_ID = "insightsId";
	public static final String GUEST = "guest";
	public static final String ID = "id";
	public static final String SOCKETS = "sockets";
	public static final String MEASUREMENTS = "measurements";

}

