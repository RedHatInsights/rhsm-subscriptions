package com.redhat.swatch.azure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class BillableUsageAggregationKeySerde implements Serde<BillableUsageAggregationKey> {

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    Serde.super.configure(configs, isKey);
  }

  @Override
  public void close() {
    Serde.super.close();
  }

  @Override
  public Serializer<BillableUsageAggregationKey> serializer() {
    return new Serializer<BillableUsageAggregationKey>() {
      @Override
      public byte[] serialize(String topic, BillableUsageAggregationKey data) {
        ObjectMapper mapper = new ObjectMapper();
        try {
          return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Cannot serialize data");
        }
      }
    };
  }

  @Override
  public Deserializer<BillableUsageAggregationKey> deserializer() {
    return new Deserializer<BillableUsageAggregationKey>() {

      @Override
      public BillableUsageAggregationKey deserialize(String topic, byte[] data) {
        ObjectMapper mapper = new ObjectMapper();
        try {
          return mapper.readValue(data, BillableUsageAggregationKey.class);
        } catch (IOException e) {
          throw new IllegalArgumentException("Cannot deserialize data");
        }
      }
    };
  }
}
