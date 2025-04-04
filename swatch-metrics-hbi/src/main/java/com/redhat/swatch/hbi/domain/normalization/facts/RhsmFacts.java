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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents the facts related to the RHSM (Red Hat Subscription Management) namespace.
 *
 * <p>This class provides a structured representation of system information derived from the "rhsm"
 * namespace, enabling efficient parsing and utilization of system purpose and subscription data.
 * Facts are extracted and validated from the provided {@link HbiHostFacts} object.
 *
 * <p>Constants: - RHSM_FACTS_NAMESPACE: The namespace for RHSM-specific facts. -
 * SYNC_TIMESTAMP_FACT: Represents the system last sync timestamp fact. - SLA_FACT: Represents the
 * Service Level Agreement fact. - USAGE_FACT: Represents the system purpose usage fact. -
 * SYSTEM_PURPOSE_ROLE_FACT: Represents the system purpose role fact. - SYSTEM_PURPOSE_UNITS_FACT:
 * Represents the system purpose units fact. - IS_VIRTUAL_FACT: Represents whether the system is a
 * virtual machine. - BILLING_MODEL: Represents the billing model fact for the system. - GUEST_ID:
 * Represents the fact identifying the guest system in a virtualized environment. -
 * PRODUCT_IDS_FACT: Represents the set of product identifiers registered in RHSM.
 *
 * <p>Fields: - sla: The service level agreement associated with the system. - usage: The purpose or
 * usage scope of the system. - syncTimestamp: The last-known synchronization timestamp. -
 * isVirtual: Indicates whether the system is a virtual machine. - systemPurposeRole: The role
 * defined for the system purpose. - systemPurposeUnits: The units associated with the system
 * purpose. - billingModel: The billing model associated with the system. - guestId: The unique
 * identifier of the guest system in a virtualized environment. - productIds: A set of product
 * identifiers registered for the system in RHSM.
 *
 * <p>Constructors: - RhsmFacts(String sla, String usage, String syncTimestamp, Boolean isVirtual,
 * String systemPurposeRole, String systemPurposeUnits, String billingModel, String guestId,
 * Set<String> productIds): Constructs a fully initialized instance with the specified fields.
 *
 * <p>- RhsmFacts(HbiHostFacts facts): Constructs an instance by parsing and validating an {@link
 * HbiHostFacts} object. The namespace must match the "rhsm" namespace, and the provided facts
 * object must not be null. Extracted facts are validated, and defaults are initialized where
 * applicable.
 *
 * <p>Key Functionality: - Validates that the namespace of the provided {@link HbiHostFacts} object
 * matches "rhsm". - Initializes fields by extracting information from the raw facts in a structured
 * manner. - Fields are nullable if the corresponding data is not present in the provided facts
 * object. - Ensures non-null collections are used for fields like productIds, even if no data
 * exists.
 */
@Getter
@AllArgsConstructor
@Builder
public class RhsmFacts {

  public static final String RHSM_FACTS_NAMESPACE = "rhsm";
  public static final String SYNC_TIMESTAMP_FACT = "SYNC_TIMESTAMP";
  public static final String SLA_FACT = "SYSPURPOSE_SLA";
  public static final String USAGE_FACT = "SYSPURPOSE_USAGE";
  public static final String SYSTEM_PURPOSE_ROLE_FACT = "SYSPURPOSE_ROLE";
  public static final String SYSTEM_PURPOSE_UNITS_FACT = "SYSPURPOSE_UNITS";
  public static final String IS_VIRTUAL_FACT = "IS_VIRTUAL";
  public static final String BILLING_MODEL = "BILLING_MODEL";
  public static final String GUEST_ID = "GUEST_ID";
  public static final String PRODUCT_IDS_FACT = "RH_PROD";

  private final String sla;
  private final String usage;
  private final String syncTimestamp;
  private final Boolean isVirtual;
  private final String systemPurposeRole;
  private final String systemPurposeUnits;
  private final String billingModel;
  private final String guestId;
  private final Set<String> productIds;

  @SuppressWarnings("unchecked")
  public RhsmFacts(HbiHostFacts facts) {
    if (facts == null) {
      throw new IllegalArgumentException("RHSM fact collection cannot be null");
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
    this.billingModel = (String) rawFacts.get(BILLING_MODEL);
    this.guestId = (String) rawFacts.get(GUEST_ID);
    this.productIds =
        new HashSet<>((List<String>) rawFacts.getOrDefault(PRODUCT_IDS_FACT, List.of()));
  }
}
