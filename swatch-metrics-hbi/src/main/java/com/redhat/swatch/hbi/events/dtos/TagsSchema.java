package com.redhat.swatch.hbi.events.dtos;

import lombok.Data;

@Data
public class TagsSchema {
  public String namespace;
  public String key;
  public String value;
}
