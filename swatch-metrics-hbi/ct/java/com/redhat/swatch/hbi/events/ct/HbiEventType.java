package com.redhat.swatch.hbi.events.ct;

public enum HbiEventType {
  INSTANCE_CREATED("created"),
  INSTANCE_UPDATED("updated"),
  INSTANCE_DELETED("delete");

  private String value;

  HbiEventType(String value) {
    this.value = value;
  }
}
