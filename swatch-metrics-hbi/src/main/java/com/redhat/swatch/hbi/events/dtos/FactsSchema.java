
package com.redhat.swatch.hbi.events.dtos;

import java.util.Map;
import lombok.Data;

@Data
public class FactsSchema {
    public String namespace;
    public Map<String, String> facts;
}
