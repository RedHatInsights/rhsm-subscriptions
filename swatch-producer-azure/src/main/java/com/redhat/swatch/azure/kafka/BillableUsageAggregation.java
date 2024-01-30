package com.redhat.swatch.azure.kafka;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@RegisterForReflection
@Getter
@Setter
@EqualsAndHashCode
public class BillableUsageAggregation {

  private BillableUsageAggregationKey key;

  private BigDecimal totalValue = new BigDecimal(0);


  public BillableUsageAggregation updateFrom(BillableUsage billableUsage) {
    if(key == null) {
      key =  new BillableUsageAggregationKey(billableUsage);;
    }
   totalValue = totalValue.add(billableUsage.getValue());
    return this;
  }

}
