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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RhsmFacts {

  public static final String RHSM_FACTS_NAMESPACE = "rhsm";
  public static final String SYNC_TIMESTAMP_FACT = "SYNC_TIMESTAMP";
  public static final String SLA_FACT = "SYSPURPOSE_SLA";
  public static final String USAGE_FACT = "SYSPURPOSE_USAGE";
  public static final String SYSTEM_PURPOSE_ROLE_FACT = "SYSPURPOSE_ROLE";
  public static final String SYSTEM_PURPOSE_UNITS_FACT = "SYSPURPOSE_UNITS";
  public static final String IS_VIRTUAL_FACT = "IS_VIRTUAL";
  public static final String PRODUCT_IDS_FACT = "RH_PROD";

  private final String sla;
  private final String usage;
  private final String syncTimestamp;
  private final Boolean isVirtual;
  private final String systemPurposeRole;
  private final String systemPurposeUnits;
  private final Set<String> productIds;

  @SuppressWarnings("unchecked")
  public RhsmFacts(HbiHostFacts facts) {
    if (facts == null) {
      throw new IllegalArgumentException("RHSM fact collection can not be null");
    }

    if (!RHSM_FACTS_NAMESPACE.equalsIgnoreCase(facts.getNamespace())) {
      throw new IllegalArgumentException("Invalid HBI fact namespace for 'rhsm' facts.");
    }

    Map<String, Object> rawFacts = Optional.ofNullable(facts.getFacts()).orElse(new HashMap<>());
    this.sla = (String) rawFacts.get(SLA_FACT);
    this.usage = (String) rawFacts.get(USAGE_FACT);
    this.syncTimestamp = (String) rawFacts.get(SYNC_TIMESTAMP_FACT);
    this.isVirtual = (Boolean) rawFacts.get(IS_VIRTUAL_FACT);
    this.systemPurposeRole = (String) rawFacts.get(SYSTEM_PURPOSE_ROLE_FACT);
    this.systemPurposeUnits = (String) rawFacts.get(SYSTEM_PURPOSE_UNITS_FACT);
    this.productIds =
        new HashSet<>((List<String>) rawFacts.getOrDefault(PRODUCT_IDS_FACT, List.of()));
  }
}
