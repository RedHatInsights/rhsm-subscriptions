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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

/**
 * Audit-only BRD allocation across licensed contracts for a billing account. Emits one INFO line
 * per billable usage with a key=value preface and JSON allocation payload for Sumo reconstruction.
 */
@Slf4j
@ApplicationScoped
public class ContractAllocationAuditService {

  private static final ObjectMapper ALLOCATION_JSON = new ObjectMapper();

  static final Comparator<Contract> ALLOCATION_ORDER =
      Comparator.comparing(Contract::getEndDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(Contract::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Contract::getLicenseId, Comparator.naturalOrder());

  AllocationResult allocate(
      List<Contract> contracts, String contractMetricId, double monthToDateUsage) {
    double remainingUsage = monthToDateUsage;
    List<AllocationLine> lines = new ArrayList<>();

    List<Contract> sortedContracts =
        licensedContractsOnly(contracts).stream().sorted(ALLOCATION_ORDER).toList();

    for (Contract contract : sortedContracts) {
      double capacity = ContractMetricCapacity.sumForMetric(contract, contractMetricId);
      double allocated = Math.min(remainingUsage, capacity);
      double remainingCapacity = capacity - allocated;
      lines.add(
          new AllocationLine(contract.getLicenseId(), capacity, allocated, remainingCapacity));
      remainingUsage -= allocated;
    }

    return new AllocationResult(lines, Math.max(0.0, remainingUsage));
  }

  public void logAllocation(
      BillableUsage usage, List<Contract> contracts, String contractMetricId) {
    if (licensedContractsOnly(contracts).isEmpty()) {
      return;
    }

    Double monthToDateUsage = usage.getCurrentTotal();
    if (monthToDateUsage == null) {
      log.warn(formatMissingMonthToDateUsageWarning(usage));
      return;
    }

    AllocationResult result = allocate(contracts, contractMetricId, monthToDateUsage);
    AuditPayload payload = buildAuditPayload(contracts, contractMetricId, result);
    log.info(formatAuditLogLine(usage, monthToDateUsage, payload));
  }

  static AuditPayload buildAuditPayload(
      List<Contract> contracts, String contractMetricId, AllocationResult result) {
    List<AuditPayload.LicensedEntry> licensed = new ArrayList<>();

    for (int index = 0; index < result.lines().size(); index++) {
      AllocationLine line = result.lines().get(index);
      licensed.add(
          new AuditPayload.LicensedEntry(
              index + 1,
              line.licenseId(),
              line.capacity(),
              line.allocated(),
              line.capacityExhausted(),
              line.remainingCapacity()));
    }

    double remainingAfterLicensed = result.unallocatedUsage();
    AuditPayload.UnlicensedSummary unlicensed = null;
    double unallocatedAboveAll = remainingAfterLicensed;

    List<Contract> unlicensedContracts = contractsWithoutLicenseId(contracts);
    if (!unlicensedContracts.isEmpty()) {
      double totalUnlicensedCapacity =
          totalCapacityForMetric(unlicensedContracts, contractMetricId);
      double usageWithoutLicenseId = Math.min(remainingAfterLicensed, totalUnlicensedCapacity);
      unlicensed =
          new AuditPayload.UnlicensedSummary(
              unlicensedContracts.size(), totalUnlicensedCapacity, usageWithoutLicenseId);
      unallocatedAboveAll = Math.max(0.0, remainingAfterLicensed - totalUnlicensedCapacity);
    }

    return new AuditPayload(licensed, unlicensed, unallocatedAboveAll);
  }

  static String formatAuditLogLine(
      BillableUsage usage, double monthToDateUsage, AuditPayload payload) {
    try {
      String allocationJson = ALLOCATION_JSON.writeValueAsString(payload);
      return formatAuditHeader(usage, monthToDateUsage) + " allocation=" + allocationJson;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize contract allocation audit", exception);
    }
  }

  static String formatAuditHeader(BillableUsage usage, double monthToDateUsage) {
    return "Contract allocation audit orgId=%s productId=%s metric=%s billingAccountId=%s snapshotDate=%s monthToDateUsage=%s"
        .formatted(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getMetricId(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate(),
            monthToDateUsage);
  }

  static String formatMissingMonthToDateUsageWarning(BillableUsage usage) {
    return "Skipping contract allocation audit because monthToDateUsage is null orgId=%s productId=%s metric=%s billingAccountId=%s snapshotDate=%s"
        .formatted(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getMetricId(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate());
  }

  private static double totalCapacityForMetric(List<Contract> contracts, String contractMetricId) {
    return contracts.stream()
        .mapToDouble(contract -> ContractMetricCapacity.sumForMetric(contract, contractMetricId))
        .sum();
  }

  private static List<Contract> licensedContractsOnly(List<Contract> contracts) {
    return contracts.stream().filter(ContractAllocationAuditService::hasLicenseId).toList();
  }

  private static List<Contract> contractsWithoutLicenseId(List<Contract> contracts) {
    return contracts.stream().filter(contract -> !hasLicenseId(contract)).toList();
  }

  private static boolean hasLicenseId(Contract contract) {
    String licenseId = contract.getLicenseId();
    return licenseId != null && !licenseId.isBlank();
  }

  record AllocationLine(
      String licenseId, double capacity, double allocated, double remainingCapacity) {

    boolean capacityExhausted() {
      return remainingCapacity <= 0.0;
    }
  }

  record AllocationResult(List<AllocationLine> lines, double unallocatedUsage) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record AuditPayload(
      List<LicensedEntry> licensed, UnlicensedSummary unlicensed, double unallocatedUsage) {

    record LicensedEntry(
        int index,
        String licenseId,
        double capacity,
        double allocated,
        boolean exhausted,
        double remainingCapacity) {}

    record UnlicensedSummary(
        int contractCount, double totalCapacity, double usageWithoutLicenseId) {}
  }
}
