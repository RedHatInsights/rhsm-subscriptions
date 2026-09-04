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
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Facts reported by the RHSM Conduit reporter (HBI namespace {@code rhsm}).
 *
 * <p>Only models facts that are actually read by HBI event normalization (see {@code RhsmFacts} in
 * {@code swatch-model-metrics-hbi}). Other facts seen in real payloads (e.g. {@code ARCHITECTURE},
 * {@code org_id}) are not normalized and are intentionally omitted.
 *
 * <pre>
 * RhsmFacts.builder()
 *     .defaultFacts()
 *     .products(List.of("69"))
 *     .build();
 * </pre>
 */
@Getter
public final class RhsmFacts {

  private static final String SLA_FACT = "SYSPURPOSE_SLA";
  private static final String USAGE_FACT = "SYSPURPOSE_USAGE";
  private static final String SYNC_TIMESTAMP_FACT = "SYNC_TIMESTAMP";
  private static final String IS_VIRTUAL_FACT = "IS_VIRTUAL";
  private static final String SYSTEM_PURPOSE_ROLE_FACT = "SYSPURPOSE_ROLE";
  private static final String SYSTEM_PURPOSE_UNITS_FACT = "SYSPURPOSE_UNITS";
  private static final String BILLING_MODEL_FACT = "BILLING_MODEL";
  private static final String GUEST_ID_FACT = "GUEST_ID";
  private static final String PRODUCT_IDS_FACT = "RH_PROD";

  private final String sla;
  private final String usage;
  private final String syncTimestamp;
  private final Boolean isVirtual;
  private final String systemPurposeRole;
  private final String systemPurposeUnits;
  private final String billingModel;
  private final String guestId;
  private final List<String> products;

  // Fully-qualified: a bare `@Builder` here would resolve to the nested Builder class below
  // instead of lombok.Builder, since a member type shadows a same-named import in its own body.
  @lombok.Builder(builderClassName = "Builder")
  private RhsmFacts(
      String sla,
      String usage,
      String syncTimestamp,
      Boolean isVirtual,
      String systemPurposeRole,
      String systemPurposeUnits,
      String billingModel,
      String guestId,
      List<String> products) {
    this.sla = sla;
    this.usage = usage;
    this.syncTimestamp = syncTimestamp;
    this.isVirtual = isVirtual;
    this.systemPurposeRole = systemPurposeRole;
    this.systemPurposeUnits = systemPurposeUnits;
    this.billingModel = billingModel;
    this.guestId = guestId;
    this.products = products;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convert to the raw key/value map HBI expects for the {@code rhsm} facts namespace. Only fields
   * that were explicitly set are included.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> facts = new LinkedHashMap<>();
    putIfPresent(facts, SLA_FACT, sla);
    putIfPresent(facts, USAGE_FACT, usage);
    putIfPresent(facts, SYNC_TIMESTAMP_FACT, syncTimestamp);
    putIfPresent(facts, IS_VIRTUAL_FACT, isVirtual == null ? null : String.valueOf(isVirtual));
    putIfPresent(facts, SYSTEM_PURPOSE_ROLE_FACT, systemPurposeRole);
    putIfPresent(facts, SYSTEM_PURPOSE_UNITS_FACT, systemPurposeUnits);
    putIfPresent(facts, BILLING_MODEL_FACT, billingModel);
    putIfPresent(facts, GUEST_ID_FACT, guestId);
    putIfPresent(facts, PRODUCT_IDS_FACT, products);
    return facts;
  }

  private static void putIfPresent(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }

  public static class Builder {
    /** Seed physical, non-virtual RHEL defaults. Call first, then override as needed. */
    public Builder defaultFacts() {
      this.isVirtual = false;
      this.products = List.of("69");
      return this;
    }
  }
}
