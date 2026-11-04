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
package com.redhat.swatch.billable.usage.services.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillableUsageCalculationTest {

  private static final OffsetDateTime REMITTANCE_DATE =
      OffsetDateTime.parse("2026-10-30T12:00:00Z");

  @Nested
  class RemainingLicenseCapacity {

    @Test
    void subtractsAlreadyCoveredFromLiveCapacity() {
      SequencedMap<String, Double> remaining =
          BillableUsageCalculation.remainingLicenseCapacity(
              usage(0.0), coverage(Map.of("license-a", 100.0)), Map.of("license-a", 40.0));

      assertEquals(60.0, remaining.get("license-a"));
    }

    @Test
    void ignoresAlreadyCoveredOnExpiredLicenses() {
      SequencedMap<String, Double> remaining =
          BillableUsageCalculation.remainingLicenseCapacity(
              usage(0.0), coverage(Map.of("license-b", 40.0)), Map.of("license-a", 80.0));

      assertEquals(40.0, remaining.get("license-b"));
      assertEquals(1, remaining.size());
    }

    @Test
    void clampsRoomAtZeroWhenCoveredExceedsCapacity() {
      SequencedMap<String, Double> remaining =
          BillableUsageCalculation.remainingLicenseCapacity(
              usage(0.0), coverage(Map.of("license-a", 40.0)), Map.of("license-a", 80.0));

      assertEquals(0.0, remaining.get("license-a"));
    }
  }

  @Nested
  class AllocateCovered {

    @Test
    void fillsLicensesInInsertionOrder() {
      SequencedMap<String, Double> rooms = new LinkedHashMap<>();
      rooms.put("license-a", 40.0);
      rooms.put("license-b", 30.0);

      SequencedMap<String, Double> covered = BillableUsageCalculation.allocateCovered(55.0, rooms);

      assertEquals(40.0, covered.get("license-a"));
      assertEquals(15.0, covered.get("license-b"));
      assertEquals(2, covered.size());
    }

    @Test
    void stopsWhenDeltaIsExhausted() {
      SequencedMap<String, Double> rooms = new LinkedHashMap<>();
      rooms.put("license-a", 40.0);
      rooms.put("license-b", 30.0);

      SequencedMap<String, Double> covered = BillableUsageCalculation.allocateCovered(25.0, rooms);

      assertEquals(Map.of("license-a", 25.0), covered);
    }
  }

  @Nested
  class Calculate {

    @Test
    void firstTallyUnderContractBooksCoveredOnly() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(80.0),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(0.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getBillableValue());
      assertEquals(Map.of("license-a", 80.0), calc.getCoveredDeltaByLicense());
      assertEquals(REMITTANCE_DATE, calc.getRemittanceDate());
    }

    @Test
    void remitsOverageAboveLiveCoverage() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(0.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(30.0, calc.getRemittedValue());
      assertEquals(30.0, calc.getBillableValue());
      assertEquals(Map.of("license-a", 100.0), calc.getCoveredDeltaByLicense());
    }

    @Test
    void doesNotDoubleBillAlreadyRemittedOverage() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(30.0, Map.of("license-a", 100.0)),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void remitsOnlyNewOverageAbovePriorRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(150.0),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(30.0, Map.of("license-a", 100.0)),
              REMITTANCE_DATE);

      assertEquals(20.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void replacementLicenseAbsorbsGrowthWithoutRemittance() {
      // already COVERED 80 on expired A; live B capacity 40; usage grows to 90
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              coverage(Map.of("license-b", 40.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0)),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(Map.of("license-b", 10.0), calc.getCoveredDeltaByLicense());
    }

    @Test
    void replacementLicenseRemitsOnlyAboveStackedFloor() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              coverage(Map.of("license-b", 40.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0)),
              REMITTANCE_DATE);

      assertEquals(10.0, calc.getRemittedValue());
      assertEquals(Map.of("license-b", 40.0), calc.getCoveredDeltaByLicense());
    }

    @Test
    void expiredCoveredDoesNotReduceLiveLicenseCapacity() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(150.0),
              coverage(Map.of("license-y", 40.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0, "license-y", 20.0)),
              REMITTANCE_DATE);

      assertEquals(30.0, calc.getRemittedValue());
      assertEquals(Map.of("license-y", 20.0), calc.getCoveredDeltaByLicense());
    }

    @Test
    void sameLicenseCapacityDropRemitsGrowthAboveWatermark() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              coverage(Map.of("license-a", 40.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0)),
              REMITTANCE_DATE);

      assertEquals(10.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void emptyLiveCoverageOnReplacementRemitsGrowthAboveWatermark() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(85.0),
              coverage(Map.of("license-b", 0.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0)),
              REMITTANCE_DATE);

      assertEquals(5.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void raisingLiveCoverageDoesNotIssueCreditAgainstPriorRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(30.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(Map.of("license-a", 60.0), calc.getCoveredDeltaByLicense());
    }

    @Test
    void nullCurrentTotalYieldsZeros() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(null),
              coverage(Map.of("license-a", 100.0)),
              new RemittanceTotals(0.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getBillableValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void unchangedTotalUnderReplacementDoesNotRemitOrBookCovered() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(80.0),
              coverage(Map.of("license-b", 40.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 80.0)),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void noContractCoverageRemitsFullMonthToDateMinusPriorRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(50.0),
              coverage(Map.of()),
              new RemittanceTotals(20.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(30.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void splitsCoveredAcrossCoLiveLicensesInAllocationOrder() {
      SequencedMap<String, Double> capacities = new LinkedHashMap<>();
      capacities.put("license-a", 40.0);
      capacities.put("license-b", 30.0);

      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(55.0),
              coverage(capacities),
              new RemittanceTotals(0.0, Map.of()),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(40.0, calc.getCoveredDeltaByLicense().get("license-a"));
      assertEquals(15.0, calc.getCoveredDeltaByLicense().get("license-b"));
    }

    @Test
    void coLiveSurvivorUsesOwnCoveredShareAfterPeerExpires() {
      // A:40 B:15 already COVERED; A gone; usage grows to 70 → only B room 15 left
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(70.0),
              coverage(Map.of("license-b", 30.0)),
              new RemittanceTotals(0.0, Map.of("license-a", 40.0, "license-b", 15.0)),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(Map.of("license-b", 15.0), calc.getCoveredDeltaByLicense());
    }
  }

  private static BillableUsage usage(Double currentTotal) {
    return new BillableUsage()
        .withProductId("rosa")
        .withMetricId("Instance-hours")
        .withBillingProvider(BillableUsage.BillingProvider.AWS)
        .withCurrentTotal(currentTotal)
        .withValue(currentTotal == null ? 0.0 : currentTotal);
  }

  private static ContractCoverage coverage(Map<String, Double> capacityByLicense) {
    SequencedMap<String, Double> sequenced = sequenced(capacityByLicense);
    double total = sequenced.values().stream().mapToDouble(Double::doubleValue).sum();
    return ContractCoverage.builder()
        .total(total)
        .gratis(false)
        .capacityByLicense(Collections.unmodifiableSequencedMap(sequenced))
        .build();
  }

  private static SequencedMap<String, Double> sequenced(Map<String, Double> values) {
    return new LinkedHashMap<>(values);
  }
}
