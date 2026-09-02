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
package com.redhat.swatch.component.tests.api.hbi;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Facts reported by the Satellite reporter (HBI namespace {@code satellite}).
 *
 * <p>Only models facts that are actually read by HBI event normalization (see {@code
 * SatelliteFacts} in {@code swatch-model-metrics-hbi}). Other facts seen in real payloads (e.g.
 * {@code satellite_version}, {@code virtual_host_name}) are not normalized and are intentionally
 * omitted.
 */
@Getter
public final class SatelliteFacts {

  private static final String SLA_FACT = "system_purpose_sla";
  private static final String USAGE_FACT = "system_purpose_usage";
  private static final String ROLE_FACT = "system_purpose_role";
  private static final String VIRTUAL_HOST_UUID_FACT = "virtual_host_uuid";

  private final String sla;
  private final String usage;
  private final String role;
  private final String hypervisorUuid;

  // Fully-qualified: a bare `@Builder` here would resolve to the nested Builder class below
  // instead of lombok.Builder, since a member type shadows a same-named import in its own body.
  @lombok.Builder(builderClassName = "Builder")
  private SatelliteFacts(String sla, String usage, String role, String hypervisorUuid) {
    this.sla = sla;
    this.usage = usage;
    this.role = role;
    this.hypervisorUuid = hypervisorUuid;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convert to the raw key/value map HBI expects for the {@code satellite} facts namespace. Only
   * fields that were explicitly set are included.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> facts = new LinkedHashMap<>();
    putIfPresent(facts, SLA_FACT, sla);
    putIfPresent(facts, USAGE_FACT, usage);
    putIfPresent(facts, ROLE_FACT, role);
    putIfPresent(facts, VIRTUAL_HOST_UUID_FACT, hypervisorUuid);
    return facts;
  }

  private static void putIfPresent(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }

  public static class Builder {
    /** Seed the common Satellite defaults used across component tests. */
    public Builder defaultFacts() {
      this.role = "Red Hat Enterprise Linux Server";
      this.sla = "Premium";
      this.usage = "Production";
      return this;
    }
  }
}
