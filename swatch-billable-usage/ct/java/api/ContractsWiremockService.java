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

import com.redhat.swatch.component.tests.api.WiremockService;
import domain.ContractStub;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContractsWiremockService extends WiremockService {

  /**
   * Setup the contracts API to return a contract with no coverage (all usage is billable). For
   * contract-enabled products, we must return a contract (not an empty list), but with 0 coverage.
   *
   * @param orgId Organization ID
   * @param productId Product ID
   */
  public void setupNoContractCoverage(String orgId, String productId) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    registerContractStub(orgId, productId, startDate, endDate, List.of());
  }

  /**
   * Setup the contracts API to return contract coverage.
   *
   * @param orgId Organization ID
   * @param productId Product ID
   * @param metricId Contract metric ID (e.g., "redhat.com:storage_gibibytes_months")
   * @param coverageValue The amount of coverage provided by the contract
   */
  public void setupContractCoverage(
      String orgId, String productId, String metricId, double coverageValue) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    registerContractStub(
        orgId, productId, startDate, endDate, List.of(contractMetric(metricId, coverageValue)));
  }

  /**
   * Setup one contract with coverage for multiple AWS dimensions (same billable-unit value on every
   * product metric).
   *
   * @param orgId Organization ID
   * @param productId Product ID
   * @param awsDimensionToBillableUnits AWS dimension to contract value in billable units
   */
  public void setupMultiMetricContractCoverage(
      String orgId, String productId, Map<String, Double> awsDimensionToBillableUnits) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    List<Map<String, Object>> metrics =
        awsDimensionToBillableUnits.entrySet().stream()
            .map(entry -> contractMetric(entry.getKey(), entry.getValue()))
            .toList();
    registerContractStub(orgId, productId, startDate, endDate, metrics);
  }

  public void setupContracts(String orgId, String productId, List<ContractStub> contracts) {
    List<Map<String, Object>> payloads =
        contracts.stream()
            .map(
                contract ->
                    contractPayload(
                        orgId,
                        productId,
                        contract.startDate(),
                        contract.endDate(),
                        contract.metricCoverage().entrySet().stream()
                            .map(entry -> contractMetric(entry.getKey(), entry.getValue()))
                            .toList(),
                        contract.licenseId()))
            .toList();
    registerContractsStub(orgId, productId, payloads);
  }

  /**
   * Setup the contracts API to return a contract with a custom date range and no coverage. Use this
   * to simulate an inactive/expired subscription (e.g. contract that ended last month). When the
   * tally snapshot date falls outside this range, no contract is valid for that usage.
   *
   * @param orgId Organization ID
   * @param productId Product ID
   * @param startDate Contract start date (inclusive)
   * @param endDate Contract end date (exclusive for usage comparison)
   */
  public void setupContractCoverage(
      String orgId, String productId, OffsetDateTime startDate, OffsetDateTime endDate) {
    registerContractStub(orgId, productId, startDate, endDate, List.of());
  }

  /**
   * Setup the contracts API to return no contracts. Simulates contract deletion for
   * contract-enabled products; billable-usage skips processing and leaves existing remittance
   * unchanged.
   *
   * @param orgId Organization ID
   * @param productId Product ID
   */
  public void setupContractNotFound(String orgId, String productId) {
    registerContractsStub(orgId, productId, List.of());
  }

  private void registerContractStub(
      String orgId,
      String productId,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      List<?> metrics) {
    registerContractsStub(
        orgId,
        productId,
        List.of(contractPayload(orgId, productId, startDate, endDate, metrics, null)));
  }

  private static Map<String, Object> contractPayload(
      String orgId,
      String productId,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      List<?> metrics,
      String licenseId) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("org_id", orgId);
    payload.put("product_id", productId);
    payload.put("start_date", startDate.toString());
    payload.put("end_date", endDate.toString());
    payload.put("metrics", metrics);
    if (licenseId != null) {
      payload.put("license_id", licenseId);
    }
    return payload;
  }

  private static Map<String, Object> contractMetric(String metricId, double coverageValue) {
    return Map.of("metric_id", metricId, "value", coverageValue);
  }

  private void registerContractsStub(
      String orgId, String productId, List<Map<String, Object>> contracts) {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/contractApi/api/swatch-contracts/internal/contracts.*",
                    "queryParameters",
                    Map.of(
                        "org_id", Map.of("equalTo", orgId),
                        "product_tag", Map.of("equalTo", productId))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    contracts),
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Setup the contracts API to return 500 error (service unavailable).
   *
   * @param orgId Organization ID
   */
  public void setupContractServiceError(String orgId) {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    "/mock/contractApi/api/swatch-contracts/internal/contracts.*",
                    "queryParameters",
                    Map.of("org_id", Map.of("equalTo", orgId))),
                "response",
                Map.of("status", 500),
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
