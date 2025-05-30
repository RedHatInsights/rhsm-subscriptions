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
package com.redhat.swatch.hbi.events.normalization.facts;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SatelliteFacts {
  public static final String SATELLITE_FACTS_NAMESPACE = "satellite";
  public static final String SLA_FACT = "system_purpose_sla";
  public static final String USAGE_FACT = "system_purpose_usage";
  public static final String ROLE_FACT = "system_purpose_role";
  public static final String VIRTUAL_HOST_UUID_FACT = "virtual_host_uuid";

  private final String sla;
  private final String usage;
  private final String role;
  private final String hypervisorUuid;

  public SatelliteFacts(HbiHostFacts facts) {
    if (facts == null) {
      throw new IllegalArgumentException("Satellite fact collection cannot be null");
    }

    if (!SATELLITE_FACTS_NAMESPACE.equalsIgnoreCase(facts.getNamespace())) {
      throw new IllegalArgumentException("Invalid HBI fact namespace for 'satellite' facts.");
    }

    Map<String, Object> rawFacts = Optional.ofNullable(facts.getFacts()).orElse(new HashMap<>());
    this.sla = (String) rawFacts.get(SLA_FACT);
    this.usage = (String) rawFacts.get(USAGE_FACT);
    this.role = (String) rawFacts.get(ROLE_FACT);
    this.hypervisorUuid = (String) rawFacts.get(VIRTUAL_HOST_UUID_FACT);
  }
}
