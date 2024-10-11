/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.hbi.events.dtos.hbi;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class HbiHost {
  public UUID id;

  @JsonProperty("display_name")
  public String displayName;

  @JsonProperty("ansible_host")
  public String ansibleHost;

  public String account;

  @JsonProperty("org_id")
  public String orgId;

  @JsonProperty("insights_id")
  public String insightsId;

  @JsonProperty("subscription_manager_id")
  public String subscriptionManagerId;

  @JsonProperty("satellite_id")
  public String satelliteId;

  public String fqdn;

  @JsonProperty("bios_uuid")
  public String biosUuid;

  @JsonProperty("ip_addresses")
  public List<String> ipAddresses;

  @JsonProperty("mac_addresses")
  public List<String> macAddresses;

  public List<HbiHostFacts> facts;

  @JsonProperty("provider_id")
  public String providerId;

  @JsonProperty("provider_type")
  public String providerType;

  public String created;
  public String updated;

  @JsonProperty("stale_timestamp")
  public String staleTimestamp;

  @JsonProperty("stale_warning_timestamp")
  public String staleWarningTimestamp;

  @JsonProperty("culled_timestamp")
  public String culledTimestamp;

  public String reporter;
  public List<HbiEventTags> tags;

  @JsonProperty("system_profile")
  public Map<String, Object> systemProfile;

  @JsonProperty("per_reporter_staleness")
  public Map<String, Object> perReporterStaleness;

  public List<Map<String, Object>> groups;
}
