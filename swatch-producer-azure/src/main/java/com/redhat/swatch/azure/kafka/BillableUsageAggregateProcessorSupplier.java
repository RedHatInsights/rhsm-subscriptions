package com.redhat.swatch.azure.kafka;

import com.redhat.swatch.azure.openapi.model.BillableUsage;
import com.redhat.swatch.azure.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.azure.openapi.model.BillableUsage.SlaEnum;
import com.redhat.swatch.azure.openapi.model.BillableUsage.UsageEnum;
import com.redhat.swatch.azure.service.BillableUsageConsumer;
import java.util.Set;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.StoreBuilder;

public class BillableUsageAggregateProcessorSupplier implements
    ProcessorSupplier<Windowed<BillableUsageAggregationKey>, BillableUsageAggregation, String, String> {

  private BillableUsageConsumer billableUsageConsumer;

  public BillableUsageAggregateProcessorSupplier(BillableUsageConsumer billableUsageConsumer) {
    this.billableUsageConsumer = billableUsageConsumer;
  }

  @Override
  public Processor<Windowed<BillableUsageAggregationKey>, BillableUsageAggregation, String, String> get() {
    return new BillableUsageAggregateProcessor(this.billableUsageConsumer);
  }

  @Override
  public Set<StoreBuilder<?>> stores() {
    return ProcessorSupplier.super.stores();
  }

   class BillableUsageAggregateProcessor implements
      Processor<Windowed<BillableUsageAggregationKey>, BillableUsageAggregation, String, String> {

    private BillableUsageConsumer billableUsageConsumer;

    public BillableUsageAggregateProcessor(BillableUsageConsumer billableUsageConsumer) {
      this.billableUsageConsumer = billableUsageConsumer;
    }

    @Override
    public void init(ProcessorContext context) {
      Processor.super.init(context);
    }

    @Override
    public void process(Record record) {
      BillableUsageAggregationKey key = ((Windowed<BillableUsageAggregationKey>) record.key()).key();
      BillableUsageAggregation aggregate = (BillableUsageAggregation) record.value();
      var billableUsage = new BillableUsage();
      billableUsage.setUsage(UsageEnum.fromValue(key.getUsage()));
      billableUsage.setBillingAccountId(key.getBillingAccountId());
      billableUsage.setBillingProvider(BillingProviderEnum.fromValue(key.getBillingProvider()));
      billableUsage.setOrgId(key.getOrgId());
      billableUsage.setProductId(key.getProductId());
      billableUsage.setUom(key.getMetricId());
      billableUsage.setSla(SlaEnum.fromValue(key.getSla()));
      billableUsage.setValue(aggregate.getTotalValue());
      billableUsageConsumer.process(billableUsage);
    }

    @Override
    public void close() {
      Processor.super.close();
    }
  }
}
