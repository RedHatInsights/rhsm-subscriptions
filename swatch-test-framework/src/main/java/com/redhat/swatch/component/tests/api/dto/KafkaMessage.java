package com.redhat.swatch.component.tests.api.dto;

import lombok.Data;

@Data
public class KafkaMessage<T> {
  String topic;
  String key;
  T value;
}
