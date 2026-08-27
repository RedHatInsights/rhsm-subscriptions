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

import static com.redhat.swatch.billable.usage.services.ContractAllocationAuditTestFixtures.CONTRACT_METRIC_ID;
import static com.redhat.swatch.billable.usage.services.ContractAllocationAuditTestFixtures.REFERENCE_DATE;
import static com.redhat.swatch.billable.usage.services.ContractAllocationAuditTestFixtures.SNAPSHOT_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Locks SWATCH-5300 audit log field names for support reconstruction in Sumo. Format follows the
 * ticket example and Jira AC; confirm with support before changing keys.
 */
class ContractAllocationAuditServiceLogTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  private final ContractAllocationAuditService allocationAuditService =
      new ContractAllocationAuditService();

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(ContractAllocationAuditService.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @BeforeEach
  void clearCapturedLogs() {
    LOGGER_CAPTOR.clearRecords();
  }

  @Test
  void logAllocationSkipsWhenAllContractsAreUnlicensed() {
    allocationAuditService.logAllocation(
        awsUsage(80.0), List.of(contract(null, 60), contract(" ", 40)), CONTRACT_METRIC_ID);

    assertTrue(LOGGER_CAPTOR.records.isEmpty());
  }

  @Test
  void logAllocationWarnsWhenMonthToDateUsageIsNull() {
    allocationAuditService.logAllocation(
        awsUsage(null),
        List.of(contract("arn:aws:license-manager:us-east-1:1:license:licensed", 60)),
        CONTRACT_METRIC_ID);

    assertEquals(1, LOGGER_CAPTOR.records.size());
    assertEquals(Level.WARNING, LOGGER_CAPTOR.records.get(0).getLevel());
    assertTrue(LOGGER_CAPTOR.records.get(0).getMessage().contains("monthToDateUsage is null"));
  }

  @Test
  void logAllocationEmitsSingleInfoLineForLicensedContracts() throws Exception {
    allocationAuditService.logAllocation(
        awsUsage(75.0),
        List.of(
            contract("arn:aws:license-manager:us-east-1:1:license:first", 50),
            contract("arn:aws:license-manager:us-east-1:1:license:second", 50)),
        CONTRACT_METRIC_ID);

    List<LogRecord> infoRecords =
        LOGGER_CAPTOR.records.stream()
            .filter(record -> record.getLevel().equals(Level.INFO))
            .toList();
    assertEquals(1, infoRecords.size());
    assertTrue(infoRecords.get(0).getMessage().startsWith("Contract allocation audit "));
    assertTrue(infoRecords.get(0).getMessage().contains(" allocation="));
    assertEquals(
        25.0,
        parseAllocationJson(infoRecords.get(0).getMessage())
            .get("licensed")
            .get(1)
            .get("allocated")
            .asDouble());
  }

  @Test
  void formatAuditLogLineIncludesUnallocatedUsageForLicensedOnlyContracts() throws Exception {
    BillableUsage usage = awsUsage(120.0);
    Contract first = contract("arn:aws:license-manager:us-east-1:1:license:first", 50);
    Contract second = contract("arn:aws:license-manager:us-east-1:1:license:second", 50);

    var result = allocationAuditService.allocate(List.of(first, second), CONTRACT_METRIC_ID, 120.0);
    ContractAllocationAuditService.AuditPayload payload =
        ContractAllocationAuditService.buildAuditPayload(
            List.of(first, second), CONTRACT_METRIC_ID, result);
    String line = ContractAllocationAuditService.formatAuditLogLine(usage, 120.0, payload);

    JsonNode allocation = parseAllocationJson(line);
    assertEquals(20.0, allocation.get("unallocatedUsage").asDouble());
    assertNull(allocation.get("unlicensed"));
  }

  @Test
  void formatAuditLogLineIncludesPrefaceAndAllocationJson() throws Exception {
    BillableUsage usage = awsUsage(75.0);
    Contract first =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:first",
            REFERENCE_DATE.minusMonths(2),
            REFERENCE_DATE.plusMonths(1),
            50);
    Contract second =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:second",
            REFERENCE_DATE.minusMonths(1),
            REFERENCE_DATE.plusMonths(2),
            50);

    var result = allocationAuditService.allocate(List.of(second, first), CONTRACT_METRIC_ID, 75.0);
    ContractAllocationAuditService.AuditPayload payload =
        ContractAllocationAuditService.buildAuditPayload(
            List.of(second, first), CONTRACT_METRIC_ID, result);
    String line = ContractAllocationAuditService.formatAuditLogLine(usage, 75.0, payload);

    assertTrue(line.startsWith("Contract allocation audit "));
    assertTrue(line.contains("orgId=org123"));
    assertTrue(line.contains("productId=rosa"));
    assertTrue(line.contains("metric=Cores"));
    assertTrue(line.contains("billingAccountId=ba123"));
    assertTrue(line.contains("snapshotDate=" + SNAPSHOT_DATE));
    assertTrue(line.contains("monthToDateUsage=75.0"));
    assertTrue(line.contains(" allocation="));

    JsonNode allocation = parseAllocationJson(line);
    assertEquals(2, allocation.get("licensed").size());
    assertEquals(1, allocation.get("licensed").get(0).get("index").asInt());
    assertEquals(first.getLicenseId(), allocation.get("licensed").get(0).get("licenseId").asText());
    assertEquals(50.0, allocation.get("licensed").get(0).get("allocated").asDouble());
    assertEquals(25.0, allocation.get("licensed").get(1).get("allocated").asDouble());
    assertEquals(0.0, allocation.get("unallocatedUsage").asDouble());
    assertNull(allocation.get("unlicensed"));
  }

  @Test
  void formatAuditLogLineDocumentsUsageWithoutLicenseIdOnMixedContracts() throws Exception {
    BillableUsage usage = awsUsage(80.0);
    Contract unlicensed = contract(null, 60);
    Contract licensed = contract("arn:aws:license-manager:us-east-1:1:license:licensed", 60);

    var result =
        allocationAuditService.allocate(List.of(unlicensed, licensed), CONTRACT_METRIC_ID, 80.0);
    ContractAllocationAuditService.AuditPayload payload =
        ContractAllocationAuditService.buildAuditPayload(
            List.of(unlicensed, licensed), CONTRACT_METRIC_ID, result);
    String line = ContractAllocationAuditService.formatAuditLogLine(usage, 80.0, payload);

    JsonNode allocation = parseAllocationJson(line);
    assertEquals(60.0, allocation.get("licensed").get(0).get("allocated").asDouble());
    assertEquals(1, allocation.get("unlicensed").get("contractCount").asInt());
    assertEquals(60.0, allocation.get("unlicensed").get("totalCapacity").asDouble());
    assertEquals(20.0, allocation.get("unlicensed").get("usageWithoutLicenseId").asDouble());
    assertEquals(0.0, allocation.get("unallocatedUsage").asDouble());
  }

  @Test
  void formatAuditLogLineDocumentsUnallocatedUsageAboveAllCapacity() throws Exception {
    BillableUsage usage = awsUsage(130.0);
    Contract unlicensed = contract(null, 60);
    Contract licensed = contract("arn:aws:license-manager:us-east-1:1:license:licensed", 60);

    var result =
        allocationAuditService.allocate(List.of(unlicensed, licensed), CONTRACT_METRIC_ID, 130.0);
    ContractAllocationAuditService.AuditPayload payload =
        ContractAllocationAuditService.buildAuditPayload(
            List.of(unlicensed, licensed), CONTRACT_METRIC_ID, result);
    String line = ContractAllocationAuditService.formatAuditLogLine(usage, 130.0, payload);

    JsonNode allocation = parseAllocationJson(line);
    assertEquals(60.0, allocation.get("unlicensed").get("usageWithoutLicenseId").asDouble());
    assertEquals(10.0, allocation.get("unallocatedUsage").asDouble());
  }

  @Test
  void formatMissingMonthToDateUsageWarningDocumentsSkippedAudit() {
    BillableUsage usage = awsUsage(null);

    String warning = ContractAllocationAuditService.formatMissingMonthToDateUsageWarning(usage);

    assertTrue(warning.contains("monthToDateUsage is null"));
    assertTrue(warning.contains("snapshotDate=" + SNAPSHOT_DATE));
  }

  @Test
  void licensedEntriesIncludeExhaustedFlag() throws Exception {
    Contract licensed = contract("arn:aws:license-manager:us-east-1:1:license:licensed", 60);

    var result = allocationAuditService.allocate(List.of(licensed), CONTRACT_METRIC_ID, 60.0);
    ContractAllocationAuditService.AuditPayload payload =
        ContractAllocationAuditService.buildAuditPayload(
            List.of(licensed), CONTRACT_METRIC_ID, result);
    String line = ContractAllocationAuditService.formatAuditLogLine(awsUsage(60.0), 60.0, payload);

    JsonNode licensedEntry = parseAllocationJson(line).get("licensed").get(0);
    assertEquals(licensed.getLicenseId(), licensedEntry.get("licenseId").asText());
    assertTrue(licensedEntry.get("exhausted").asBoolean());
    assertEquals(0.0, licensedEntry.get("remainingCapacity").asDouble());
  }

  private static JsonNode parseAllocationJson(String auditLogLine) throws Exception {
    int allocationIndex = auditLogLine.indexOf(" allocation=");
    assertTrue(allocationIndex > 0);
    String json = auditLogLine.substring(allocationIndex + " allocation=".length());
    return OBJECT_MAPPER.readTree(json);
  }

  private static BillableUsage awsUsage(Double currentTotal) {
    BillableUsage usage =
        new BillableUsage()
            .withOrgId("org123")
            .withProductId("rosa")
            .withMetricId("Cores")
            .withBillingProvider(BillableUsage.BillingProvider.AWS)
            .withBillingAccountId("ba123")
            .withSnapshotDate(SNAPSHOT_DATE);
    if (currentTotal != null) {
      usage.withCurrentTotal(currentTotal);
    }
    return usage;
  }

  private static Contract contract(String licenseId, double capacity) {
    return contract(licenseId, SNAPSHOT_DATE.minusMonths(1), SNAPSHOT_DATE.plusMonths(1), capacity);
  }

  private static Contract contract(
      String licenseId, OffsetDateTime startDate, OffsetDateTime endDate, double capacity) {
    return new Contract()
        .licenseId(licenseId)
        .startDate(startDate)
        .endDate(endDate)
        .addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value((int) capacity));
  }

  private static final class LoggerCaptor extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
      clearRecords();
    }

    void clearRecords() {
      records.clear();
    }
  }
}
