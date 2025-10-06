package com.redhat.swatch.hbi.events.ct.api;

import com.redhat.swatch.component.tests.api.MessageValidator;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;

public class MessageValidators {
  public static MessageValidator<HbiEvent> eventMatches(String type) {
    return new MessageValidator<>(event -> type.equals(event.getType()), HbiEvent.class);
  }

  public static MessageValidator<HbiEvent> eventMatches(String type, String requestId) {
    return new MessageValidator<>(event ->
        type.equals(event.getType()) && requestId.equals(event.getMetadata().getRequestId()),
        HbiEvent.class);
  }
}
