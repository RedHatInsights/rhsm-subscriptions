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
package com.redhat.swatch.billable.usage.services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class ContractAllocationAuditTestFixtures {

  static final String CONTRACT_METRIC_ID = "four_vcpu_0";
  static final OffsetDateTime REFERENCE_DATE =
      OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  static final OffsetDateTime SNAPSHOT_DATE = REFERENCE_DATE.plusDays(14).withHour(12);

  private ContractAllocationAuditTestFixtures() {}
}
