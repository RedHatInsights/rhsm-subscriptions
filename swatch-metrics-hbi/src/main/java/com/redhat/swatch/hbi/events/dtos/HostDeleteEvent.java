
package com.redhat.swatch.hbi.events.dtos;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class HostDeleteEvent {
    public UUID id;
    public ZonedDateTime timestamp;
    public String type;
    public String account;
    public String orgId;
    public String insightsId;
    public String requestId;
    public Map<String, Object> platformMetadata;
    public HostEventMetadataSchema metadata;
}
