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

/**
 * Host data container with fluent setters.
 *
 * <p>Represents host data that will be seeded into the HBI database. Fields align with the
 * InventoryHost query to support different tally normalization scenarios.
 *
 * <p>Usage:
 *
 * <pre>
 * Host baseHost = new Host(orgId)
 *     .inventoryId("inv-123")
 *     .cores(4)
 *     .sockets(2);
 *
 * SeededHost seeded = hostManager.qpc(baseHost)
 *     .awsRhelLarge()
 *     .displayName("Custom")
 *     .insert();
 * </pre>
 */
public class RhsmHost extends Host {
  public RhsmHost(String orgId) {
    super(orgId);
    applyRhsmFactDefaults();
  }

  public RhsmHost(Host source) {
    super(source);
    if (!(source instanceof RhsmHost)) {
      applyRhsmFactDefaults();
    }
  }

  /**
   * Add a RHSM fact (stored in h.facts->'rhsm' in HBI).
   *
   * <p>Common RHSM facts: IS_VIRTUAL, RH_PROD, ARCHITECTURE, CORES, SOCKETS, BILLING_MODEL,
   * SYSPURPOSE_ROLE, SYSPURPOSE_SLA, SYSPURPOSE_USAGE
   */
  public void applyRhsmFactDefaults() {
    /// Apply reporter defaults (only if not set)
    if (getReporter() == null || "component-test".equals(getReporter())) {
      reporter("rhsm");
    }
    if (getReporters() == null
        || (getReporters().length == 1 && "component-test".equals(getReporters()[0]))) {
      reporters("rhsm");
    }
    if (getArch() == null) {
      arch("x86_64");
    }

    // Only set RHSM facts if not already present
    // Org id
    // Memory: need to determine what this is and if we need it. All the facts are set to 1
    if (!getRhsmFacts().containsKey("IS_VIRTUAL")) {
      rhsmFact("IS_VIRTUAL", "false");
    }

    if (!getRhsmFacts().containsKey("ARCHITECTURE")) {
      rhsmFact("ARCHITECTURE", "x86_64");
    }
  }
}
