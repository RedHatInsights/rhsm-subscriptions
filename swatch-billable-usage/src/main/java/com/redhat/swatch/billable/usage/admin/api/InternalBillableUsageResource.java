package com.redhat.swatch.billable.usage.admin.api;


import com.redhat.swatch.billable.usage.kafka.streams.FlushTopicService;
import com.redhat.swatch.billable.usage.openapi.model.DefaultResponse;
import com.redhat.swatch.billable.usage.openapi.resource.ApiException;
import com.redhat.swatch.billable.usage.openapi.resource.InternalBillableUsageApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class InternalBillableUsageResource implements InternalBillableUsageApi {

  private final FlushTopicService flushTopicService;

  public InternalBillableUsageResource(
      FlushTopicService flushTopicService) {
    this.flushTopicService = flushTopicService;
  }

  @Override
  public DefaultResponse flushBillableUsageAggregationTopic()
      throws ApiException, ProcessingException {
    flushTopicService.sendFlushToBillableUsageRepartitionTopic();
    return new DefaultResponse().status("Success");
  }
}