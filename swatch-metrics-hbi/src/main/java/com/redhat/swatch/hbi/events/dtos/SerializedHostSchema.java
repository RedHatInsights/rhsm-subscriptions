
package com.redhat.swatch.hbi.events.dtos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class SerializedHostSchema {
    public UUID id;
    public String displayName;
    public String ansibleHost;
    public String account;
    public String orgId;
    public String insightsId;
    public String subscriptionManagerId;
    public String satelliteId;
    public String fqdn;
    public String biosUuid;
    public List<String> ipAddresses;
    public List<String> macAddresses;
    public List<FactsSchema> facts;
    public String providerId;
    public String providerType;
    public String created;
    public String updated;
    public String staleTimestamp;
    public String staleWarningTimestamp;
    public String culledTimestamp;
    public String reporter;
    public List<TagsSchema> tags;
    public Map<String, Object> systemProfile;
    public Map<String, Object> perReporterStaleness;
    public List<Map<String, Object>> groups;
}
