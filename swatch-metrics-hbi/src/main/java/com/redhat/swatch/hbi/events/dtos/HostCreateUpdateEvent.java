
package com.redhat.swatch.hbi.events.dtos;

import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Data;


@Data
public class HostCreateUpdateEvent {
    public String type;
    public SerializedHostSchema host;
    public ZonedDateTime timestamp;
    public Map<String, Object> platformMetadata;
    public HostEventMetadataSchema metadata;
}
