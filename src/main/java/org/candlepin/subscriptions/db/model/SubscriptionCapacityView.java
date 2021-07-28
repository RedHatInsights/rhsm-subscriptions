package org.candlepin.subscriptions.db.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
@Immutable
@Subselect(
        "SELECT " +
                "sc.subscription_id,\n" +
                "sc.owner_id, \n" +
                "sc.product_id, \n" +
        "sc.sku, \n" +
        "sc.sla, \n" +
        "sc.usage, \n" +
        "sc.physical_sockets, \n" +
        "sc.virtual_sockets, \n" +
        "sc.physical_cores, \n" +
        "sc.virtual_cores, \n" +
        "sc.end_date, \n" +
                "sc.begin_date, \n" +
                "sc.account_number, \n" +
                "sc.has_unlimited_guest_sockets, \n" +
        "s.quantity, \n" +
        "s.subscription_number, \n" +
        "o.product_name \n" +
        "FROM subscription_capacity sc \n" +
        "JOIN subscription s on sc.subscription_id = s.subscription_id \n"+
                "JOIN offering o on sc.sku = o.sku"
)
@Getter
@Setter
@Builder
public class SubscriptionCapacityView {

    @EmbeddedId
    private SubscriptionCapacityKey key;

    @Column(name = "subscription_number")
    private String subscriptionNumber;

    @Column(name = "quantity")
    private long quantity;

    @Column(name = "sku")
    private String sku;

    @Column(name = "sla")
    private ServiceLevel serviceLevel;

    @Column(name = "usage")
    private Usage usage;

    @Column(name = "physical_sockets")
    private Integer physicalSockets;

    @Column(name = "virtual_sockets")
    private Integer virtualSockets;

    @Column(name = "physical_cores")
    private Integer physicalCores;

    @Column(name = "virtual_cores")
    private Integer virtualCores;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "begin_date")
    private OffsetDateTime beginDate;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "has_unlimited_guest_sockets")
    private boolean hasUnlimitedGuestSockets;

}
