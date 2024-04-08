package com.redhat.swatch.azure.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.azure.openapi.model.DefaultResponse;
import com.redhat.swatch.azure.openapi.resource.ApiException;
import com.redhat.swatch.azure.openapi.resource.InternalBillableUsageApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class BillableUsageAggregationInternalResource implements InternalBillableUsageApi {

  private final KafkaFlushService kafkaFlushService;

  public BillableUsageAggregationInternalResource(
      KafkaFlushService kafkaFlushService) {
    this.kafkaFlushService = kafkaFlushService;
  }


  @Override
  public DefaultResponse purgeKafkaTopics() throws ApiException, ProcessingException {
    System.out.println("hey");
    kafkaFlushService.sendFlushToBillableUsageRepartitionTopic();
    return new DefaultResponse().status("Success");
  }
}
