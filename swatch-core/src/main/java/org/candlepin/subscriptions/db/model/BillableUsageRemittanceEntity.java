package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
@Entity
@Table(name = "billable_usage_remittance")
@IdClass(BillableUsageRemittanceEntityPK.class)
public class BillableUsageRemittanceEntity {

  @Id
  @Column(name = "account_number", nullable = false, length = 32)
  private String accountNumber;

  @Id
  @Column(name = "product_id", nullable = false, length = 32)
  private String productId;

  @Id
  @Column(name = "metric_id", nullable = false, length = 32)
  private String metricId;

  @Id
  @Column(name = "month", nullable = false, length = 255)
  private String month;

  @Basic
  @Column(name = "remitted_value", nullable = false, precision = 0)
  private Double remittedValue;

  @Basic
  @Column(name = "remittance_date", nullable = false)
  private OffsetDateTime remittanceDate;

  @Id
  @Column(name = "granularity", nullable = false, length = 32)
  private String granularity;

  @Id
  @Column(name = "snapshot_date", nullable = false)
  private OffsetDateTime snapshotDate;

  @Id
  @Column(name = "sla", nullable = false, length = 32)
  private String sla;

  @Id
  @Column(name = "usage", nullable = false, length = 32)
  private String usage;

  @Id
  @Column(name = "billing_provider", nullable = false, length = 32)
  private String billingProvider;

  @Id
  @Column(name = "billing_account_id", nullable = false, length = 255)
  private String billingAccountId;
}
