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

import domain.ContractStub;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContractsApiStubs {

  private final ContractsWiremockService wiremockService;

  public ContractsApiStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  public void stubNoContractCoverage(String orgId, Product product) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    registerContractStub(orgId, product.getName(), startDate, endDate, List.of());
  }

  public void stubContractCoverage(
      String orgId, Product product, String metricDimension, double coverageValue) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    registerContractStub(
        orgId,
        product.getName(),
        startDate,
        endDate,
        List.of(contractMetric(metricDimension, coverageValue)));
  }

  public void stubMultiMetricContractCoverage(
      String orgId, Product product, Map<String, Double> awsDimensionToBillableUnits) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    List<Map<String, Object>> metrics =
        awsDimensionToBillableUnits.entrySet().stream()
            .map(entry -> contractMetric(entry.getKey(), entry.getValue()))
            .toList();
    registerContractStub(orgId, product.getName(), startDate, endDate, metrics);
  }

  public void stubContracts(String orgId, Product product, List<ContractStub> contracts) {
    List<Map<String, Object>> payloads =
        contracts.stream()
            .map(contract ->
                contractPayload(
                    orgId,
                    product.getName(),
                    contract.startDate(),
                    contract.endDate(),
                    contract.metricCoverage().entrySet().stream()
                        .map(entry -> contractMetric(entry.getKey(), entry.getValue()))
                        .toList(),
                    contract.licenseId()))
            .toList();
    registerContractsStub(orgId, product.getName(), payloads);
  }

  public void stubContractCoverage(
      String orgId, Product product, OffsetDateTime startDate, OffsetDateTime endDate) {
    registerContractStub(orgId, product.getName(), startDate, endDate, List.of());
  }

  public void stubContractNotFound(String orgId, Product product) {
    registerContractsStub(orgId, product.getName(), List.of());
  }

  public void stubContractServiceError(String orgId) {
    wiremockService
        .given()
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
                10,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
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
    wiremockService
        .given()
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
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}
