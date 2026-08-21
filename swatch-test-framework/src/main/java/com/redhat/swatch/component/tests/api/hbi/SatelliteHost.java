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
public class SatelliteHost extends Host {

  public SatelliteHost(String orgId) {
    super(orgId);
    // Sets the default values for Satellite hosts
    applySatelliteDefaults();
  }

  //    // Sets this hosts values to the host passed in
  public SatelliteHost(Host host) {
    super(host);
    if (!(host instanceof SatelliteHost)) {
      applySatelliteDefaults();
    }
  }

  // Neet to convert this over to the constructor
  /**
   * Apply Satellite-specific defaults (only if not already set).
   *
   * <p>Based on common values from FactNormalizerTest and production usage.
   *
   * <p>Defaults applied:
   *
   * <ul>
   *   <li>reporter: "satellite" (if not set)
   *   <li>reporters: ["satellite"] (if not set)
   *   <li>arch: "x86_64" (if not set)
   *   <li>system_purpose_role: "Red Hat Enterprise Linux Server" (if not set in satellite facts)
   *   <li>system_purpose_sla: "Premium" (if not set in satellite facts)
   *   <li>system_purpose_usage: "Production" (if not set in satellite facts)
   * </ul>
   *
   * @param host the host to apply defaults to
   */
  private void applySatelliteDefaults() {
    // Apply reporter defaults (only if not set)

    if (getReporter() == null || "component-test".equals(getReporter())) {
      reporter("satellite"); // Since you can have multiple reporters, which is set to this value?
    }

    // Need to determine how we should handle multiple reporters via data in Stage
    if (getReporters() == null
        || (getReporters().length == 1 && "component-test".equals(getReporters()[0]))) {
      reporters("satellite");
    }

    if (getArch() == null) {
      arch("x86_64"); // verify with data
    }

    // Apply Satellite fact defaults (only if not already set)
    if (!getSatelliteFacts().containsKey("system_purpose_role")) {
      satelliteFact("system_purpose_role", "Red Hat Enterprise Linux Server");
    }

    if (!getSatelliteFacts().containsKey("system_purpose_sla")) {
      satelliteFact("system_purpose_sla", "Premium");
    }

    if (!getSatelliteFacts().containsKey("system_purpose_usage")) {
      satelliteFact("system_purpose_usage", "Production");
    }
  }
}
