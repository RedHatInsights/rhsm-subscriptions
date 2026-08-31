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
package api;

import domain.Contract;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds Partner Gateway entitlement JSON fragments for Wiremock stubs. */
public final class PartnerEntitlementStubPayloads {

  private PartnerEntitlementStubPayloads() {}

  /**
   * Current purchase contract segment: startDate and dimensions only. Termination dates belong on
   * entitlementDates.endDate.
   */
  public static Map<String, Object> buildCurrentContractSegment(Contract contract) {
    return Map.of(
        "startDate",
        contract.getStartDate().format(DateTimeFormatter.ISO_INSTANT),
        "dimensions",
        buildDimensions(contract));
  }

  /** Closed historical purchase contract segment including segment endDate. */
  public static Map<String, Object> buildClosedContractSegment(Contract contract) {
    var segment = new HashMap<>(buildCurrentContractSegment(contract));
    segment.put("endDate", contract.getEndDate().format(DateTimeFormatter.ISO_INSTANT));
    return Map.copyOf(segment);
  }

  public static Map<String, String> buildEntitlementDates(Contract contract) {
    return Map.of(
        "startDate",
        contract.getStartDate().format(DateTimeFormatter.ISO_INSTANT),
        "endDate",
        contract.getEndDate().format(DateTimeFormatter.ISO_INSTANT));
  }

  public static List<Map<String, String>> buildRhEntitlements(Contract contract) {
    return List.of(
        Map.of(
            "sku",
            contract.getOffering().getSku(),
            "subscriptionNumber",
            contract.getSubscriptionNumber()));
  }

  private static List<Map<String, Object>> buildDimensions(Contract contract) {
    return contract.getContractMetrics().entrySet().stream()
        .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
        .toList();
  }
}
