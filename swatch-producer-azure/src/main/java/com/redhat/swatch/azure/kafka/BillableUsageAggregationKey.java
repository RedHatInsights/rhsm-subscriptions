package com.redhat.swatch.azure.kafka;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import io.quarkus.arc.All;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillableUsageAggregationKey {

  private String orgId;

  private String productId;

  private String metricId;

  private String accumulationPeriod;

  private String sla;

  private String usage;

  private String billingProvider;

  private String billingAccountId;

  public BillableUsageAggregationKey(BillableUsage billableUsage) {
    super();
    this.orgId = billableUsage.getOrgId();
    this.billingAccountId = billableUsage.getBillingAccountId();
    this.billingProvider = billableUsage.getBillingProvider().value();
    this.usage = billableUsage.getUsage().value();
    this.productId = billableUsage.getProductId();
    this.sla = billableUsage.getSla().value();
    this.metricId = billableUsage.getUom();
  }
}
