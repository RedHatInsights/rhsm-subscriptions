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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractAllocationAuditServiceTest {

  private final ContractAllocationAuditService allocationAuditService =
      new ContractAllocationAuditService();

  @Test
  void allocateSortsByEndDateThenStartDateThenLicenseId() {
    Contract soonestEnd =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:soonest-end",
            REFERENCE_DATE.minusMonths(2),
            REFERENCE_DATE.plusMonths(1),
            50);
    Contract laterEnd =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:later-end",
            REFERENCE_DATE.minusMonths(1),
            REFERENCE_DATE.plusMonths(2),
            50);
    Contract openEnded =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:open-ended",
            REFERENCE_DATE.minusMonths(3),
            null,
            50);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(
            List.of(openEnded, laterEnd, soonestEnd), CONTRACT_METRIC_ID, 75.0);

    assertEquals(3, result.lines().size());
    assertEquals(soonestEnd.getLicenseId(), result.lines().get(0).licenseId());
    assertEquals(50.0, result.lines().get(0).allocated());
    assertTrue(result.lines().get(0).capacityExhausted());

    assertEquals(laterEnd.getLicenseId(), result.lines().get(1).licenseId());
    assertEquals(25.0, result.lines().get(1).allocated());
    assertEquals(25.0, result.lines().get(1).remainingCapacity());

    assertEquals(openEnded.getLicenseId(), result.lines().get(2).licenseId());
    assertEquals(0.0, result.lines().get(2).allocated());
    assertEquals(0.0, result.unallocatedUsage());
  }

  @Test
  void allocateUsesNewerStartDateWhenEndDatesMatch() {
    OffsetDateTime sharedEnd = REFERENCE_DATE.plusMonths(3);
    Contract newerStart =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:newer",
            REFERENCE_DATE.minusDays(10),
            sharedEnd,
            40);
    Contract olderStart =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:older",
            REFERENCE_DATE.minusMonths(2),
            sharedEnd,
            40);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(olderStart, newerStart), CONTRACT_METRIC_ID, 50.0);

    assertEquals(newerStart.getLicenseId(), result.lines().get(0).licenseId());
    assertEquals(40.0, result.lines().get(0).allocated());
    assertEquals(olderStart.getLicenseId(), result.lines().get(1).licenseId());
    assertEquals(10.0, result.lines().get(1).allocated());
  }

  @Test
  void allocateUsesLexicographicLicenseIdWhenStartAndEndDatesMatch() {
    OffsetDateTime sharedStart = REFERENCE_DATE.minusMonths(1);
    OffsetDateTime sharedEnd = REFERENCE_DATE.plusMonths(1);
    Contract largerLicense =
        contract("arn:aws:license-manager:us-east-1:1:license:z", sharedStart, sharedEnd, 30);
    Contract smallerLicense =
        contract("arn:aws:license-manager:us-east-1:1:license:a", sharedStart, sharedEnd, 30);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(
            List.of(largerLicense, smallerLicense), CONTRACT_METRIC_ID, 35.0);

    assertEquals(smallerLicense.getLicenseId(), result.lines().get(0).licenseId());
    assertEquals(30.0, result.lines().get(0).allocated());
    assertEquals(largerLicense.getLicenseId(), result.lines().get(1).licenseId());
    assertEquals(5.0, result.lines().get(1).allocated());
  }

  @Test
  void allocateFillsFirstContractOnlyWhenUsageWithinCapacity() {
    Contract first = contract("arn:aws:license-manager:us-east-1:1:license:first", 100);
    Contract second = contract("arn:aws:license-manager:us-east-1:1:license:second", 100);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(first, second), CONTRACT_METRIC_ID, 75.0);

    ContractAllocationAuditService.AllocationLine firstLine = result.lines().get(0);
    assertEquals(75.0, firstLine.allocated());
    assertFalse(firstLine.capacityExhausted());
    assertEquals(25.0, firstLine.remainingCapacity());

    ContractAllocationAuditService.AllocationLine secondLine = result.lines().get(1);
    assertEquals(0.0, secondLine.allocated());
    assertEquals(0.0, result.unallocatedUsage());
  }

  @Test
  void allocateSpillsToSecondContractWhenFirstCapacityIsExhausted() {
    Contract first = contract("arn:aws:license-manager:us-east-1:1:license:first", 50);
    Contract second = contract("arn:aws:license-manager:us-east-1:1:license:second", 50);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(first, second), CONTRACT_METRIC_ID, 75.0);

    assertEquals(50.0, result.lines().get(0).allocated());
    assertTrue(result.lines().get(0).capacityExhausted());
    assertEquals(25.0, result.lines().get(1).allocated());
    assertEquals(25.0, result.lines().get(1).remainingCapacity());
    assertEquals(0.0, result.unallocatedUsage());
  }

  @Test
  void allocateLeavesRemainderUnallocatedWhenUsageExceedsTotalCapacity() {
    Contract first = contract("arn:aws:license-manager:us-east-1:1:license:first", 50);
    Contract second = contract("arn:aws:license-manager:us-east-1:1:license:second", 50);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(first, second), CONTRACT_METRIC_ID, 120.0);

    assertEquals(50.0, result.lines().get(0).allocated());
    assertEquals(50.0, result.lines().get(1).allocated());
    assertEquals(20.0, result.unallocatedUsage());
  }

  @Test
  void allocateExcludesContractsWithoutLicenseId() {
    Contract unlicensed = contract(null, 60);
    Contract licensed = contract("arn:aws:license-manager:us-east-1:1:license:licensed", 60);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(unlicensed, licensed), CONTRACT_METRIC_ID, 80.0);

    assertEquals(1, result.lines().size());
    assertEquals(licensed.getLicenseId(), result.lines().get(0).licenseId());
    assertEquals(60.0, result.lines().get(0).allocated());
    assertEquals(20.0, result.unallocatedUsage());
  }

  @Test
  void allocateExcludesBlankLicenseIdContracts() {
    OffsetDateTime sharedStart = REFERENCE_DATE.minusMonths(1);
    OffsetDateTime sharedEnd = REFERENCE_DATE.plusMonths(1);
    Contract blankLicense = contract(" ", sharedStart, sharedEnd, 30);
    Contract licensed =
        contract(
            "arn:aws:license-manager:us-east-1:1:license:licensed", sharedStart, sharedEnd, 30);

    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(blankLicense, licensed), CONTRACT_METRIC_ID, 35.0);

    assertEquals(1, result.lines().size());
    assertEquals(licensed.getLicenseId(), result.lines().get(0).licenseId());
    assertEquals(30.0, result.lines().get(0).allocated());
    assertEquals(5.0, result.unallocatedUsage());
  }

  @Test
  void allocateReturnsEmptyWhenAllContractsLackLicenseId() {
    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(
            List.of(contract(null, 60), contract(" ", 40)), CONTRACT_METRIC_ID, 80.0);

    assertTrue(result.lines().isEmpty());
    assertEquals(80.0, result.unallocatedUsage());
  }

  @Test
  void allocateReturnsEmptyWhenNoContractHasLicenseId() {
    ContractAllocationAuditService.AllocationResult result =
        allocationAuditService.allocate(List.of(contract(null, 60)), CONTRACT_METRIC_ID, 80.0);

    assertTrue(result.lines().isEmpty());
    assertEquals(80.0, result.unallocatedUsage());
  }

  private static Contract contract(String licenseId, double capacity) {
    return contract(
        licenseId, REFERENCE_DATE.minusMonths(1), REFERENCE_DATE.plusMonths(1), capacity);
  }

  private static Contract contract(
      String licenseId, OffsetDateTime startDate, OffsetDateTime endDate, double capacity) {
    return new Contract()
        .licenseId(licenseId)
        .startDate(startDate)
        .endDate(endDate)
        .addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value((int) capacity));
  }
}
