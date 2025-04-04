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
package com.redhat.swatch.hbi.domain.normalization.facts;

import com.redhat.swatch.hbi.dto.HbiHostFacts;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Encapsulates satellite-specific facts derived from a given source of host information.
 *
 * <p>This class parses and stores data associated with the "satellite" namespace, extracting
 * specific fields such as SLA (Service Level Agreement), usage, role, and the hypervisor UUID from
 * the raw facts.
 *
 * <p>Constants: - SATELLITE_FACTS_NAMESPACE: The expected namespace identifier for satellite facts.
 * - SLA_FACT: Key used to retrieve the SLA field from the raw facts. - USAGE_FACT: Key used to
 * retrieve the usage field from the raw facts. - ROLE_FACT: Key used to retrieve the role field
 * from the raw facts. - VIRTUAL_HOST_UUID_FACT: Key used to retrieve the hypervisor UUID from the
 * raw facts.
 *
 * <p>Fields: - sla: Represents the SLA associated with the system. - usage: Represents the intended
 * system usage purpose. - role: Represents the role of the system. - hypervisorUuid: Represents the
 * hypervisor UUID of the virtual host.
 *
 * <p>Constructors: - SatelliteFacts(String sla, String usage, String role, String hypervisorUuid):
 * Initializes the object with specific field values. - SatelliteFacts(HbiHostFacts facts):
 * Initializes the object by extracting relevant data from an {@link HbiHostFacts} object. Throws an
 * {@link IllegalArgumentException} if the facts are null or if the namespace does not match
 * "satellite".
 *
 * <p>Key Functionality: - Validates that the provided facts belong to the "satellite" namespace. -
 * Extracts and assigns specific satellite fact values from the raw facts map. - Ensures reliable
 * handling of null or non-existent fact entries by initializing corresponding fields to null if not
 * found in the raw facts.
 */
@Getter
@AllArgsConstructor
@Builder
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
