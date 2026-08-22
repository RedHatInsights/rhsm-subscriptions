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
public class QpcHost extends Host {
  public QpcHost(String orgId) {
    super(orgId);
    applyQpcFactDefaults();
  }

  public QpcHost(Host host) {
    super(host);
    if (!(host instanceof QpcHost)) {
      applyQpcFactDefaults();
    }
  }

  private void applyQpcFactDefaults() {
    // Apply reporter defaults (only if not set)
    // Need an appending strategy for multiple reporters
    if (getReporter() == null || "component-test".equals(getReporter())) {
      reporter("qpc");
    }
    // Need an appending strategy for multiple reporters
    if (getReporters() == null
        || (getReporters().length == 1 && "component-test".equals(getReporters()[0]))) {
      reporters("qpc");
    }

    if (getArch() == null) {
      arch("x86_64");
    }
  }
}
