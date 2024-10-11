
package com.redhat.swatch.hbi.events.dtos;

import lombok.Data;

@Data
public class MessageHeaders {
    public String eventType;
    public String requestId;
    public String producer;
    public String insightsId;
    public String reporter;
    public String hostType;
    public String osName;
    public String isBootc = "False";
}
