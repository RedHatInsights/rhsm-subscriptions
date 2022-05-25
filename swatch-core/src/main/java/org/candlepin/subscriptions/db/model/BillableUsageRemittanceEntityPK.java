package org.candlepin.subscriptions.db.model;

import java.io.Serializable;
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Id;
import lombok.Data;

@Data
public class BillableUsageRemittanceEntityPK implements Serializable {

  @Column(name = "account_number", nullable = false, length = 32)
  @Id
  private String accountNumber;

  @Column(name = "product_id", nullable = false, length = 32)
  @Id
  private String productId;

  @Column(name = "metric_id", nullable = false, length = 32)
  @Id
  private String metricId;

  @Column(name = "month", nullable = false, length = 255)
  @Id
  private String month;

  @Column(name = "granularity", nullable = false, length = 32)
  @Id
  private String granularity;

  @Column(name = "snapshot_date", nullable = false)
  @Id
  private OffsetDateTime snapshotDate;

  @Column(name = "sla", nullable = false, length = 32)
  @Id
  private String sla;

  @Column(name = "usage", nullable = false, length = 32)
  @Id
  private String usage;

  @Column(name = "billing_provider", nullable = false, length = 32)
  @Id
  private String billingProvider;

  @Column(name = "billing_account_id", nullable = false, length = 255)
  @Id
  private String billingAccountId;
}
