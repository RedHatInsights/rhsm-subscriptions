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
import java.util.Set;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillableUsageCalculationTest {

  private static final OffsetDateTime REMITTANCE_DATE =
      OffsetDateTime.parse("2026-10-30T12:00:00Z");

  @Nested
  class EffectiveCoverage {

    @Test
    void stacksWhenCoveredLicensesAreGoneFromLiveCoverage() {
      // prior COVERED under A; live is only B
      assertEquals(
          120.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              40.0, 80.0, Set.of("license-b"), Set.of("license-a")));
    }

    @Test
    void stacksWhenLiveCoverageIsZeroOnReplacementLicense() {
      assertEquals(
          80.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              0.0, 80.0, Set.of("license-b"), Set.of("license-a")));
    }

    @Test
    void stacksWhenCurrentLicenseSetIsEmptyButCoveredLicensesExist() {
      // live contracts without licenseId cannot overlap covered licenses
      assertEquals(
          120.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              40.0, 80.0, Set.of(), Set.of("license-a")));
    }

    @Test
    void usesMaxWhenSameLicenseStillLive() {
      assertEquals(
          80.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              40.0, 80.0, Set.of("license-a"), Set.of("license-a")));
    }

    @Test
    void usesMaxWhenLiveCoverageExceedsPriorCoveredOnSameLicense() {
      assertEquals(
          100.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              100.0, 40.0, Set.of("license-a"), Set.of("license-a")));
    }

    @Test
    void usesMaxWhenAnyCoveredLicenseStillOverlapsLive() {
      // covered A+B, live still has B → not a full replacement
      assertEquals(
          90.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              40.0, 90.0, Set.of("license-b"), Set.of("license-a", "license-b")));
    }

    @Test
    void usesMaxWhenCoveredLicenseIdsEmpty() {
      // legacy COVERED rows without licenseId
      assertEquals(
          100.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              100.0, 40.0, Set.of("license-a"), Set.of()));
    }

    @Test
    void usesMaxWhenBothLicenseSetsEmpty() {
      assertEquals(
          100.0,
          BillableUsageCalculation.computeEffectiveCoverage(100.0, 40.0, Set.of(), Set.of()));
    }

    @Test
    void zeroTotals() {
      assertEquals(
          0.0,
          BillableUsageCalculation.computeEffectiveCoverage(
              0.0, 0.0, Set.of("license-a"), Set.of("license-a")));
    }
  }

  @Nested
  class CoveredDelta {

    @Test
    void booksGrowthThatFitsUnderEffectiveCoverage() {
      assertEquals(10.0, BillableUsageCalculation.computeCoveredDelta(90.0, 120.0, 80.0, 0.0));
    }

    @Test
    void isZeroWhenTotalUnchanged() {
      assertEquals(0.0, BillableUsageCalculation.computeCoveredDelta(80.0, 120.0, 80.0, 0.0));
    }

    @Test
    void isZeroWhenTotalBelowAlreadyCovered() {
      assertEquals(0.0, BillableUsageCalculation.computeCoveredDelta(70.0, 100.0, 80.0, 0.0));
    }

    @Test
    void shiftsCoveredWindowForwardByAlreadyRemitted() {
      // min(130, 100 + 30) - 40 covered - 30 remitted = 60
      assertEquals(60.0, BillableUsageCalculation.computeCoveredDelta(130.0, 100.0, 40.0, 30.0));
    }

    @Test
    void isZeroWhenCoveredPlusRemittedAlreadyAccountForTotal() {
      // min(100, 100 + 60) - 40 - 60 = 0
      assertEquals(0.0, BillableUsageCalculation.computeCoveredDelta(100.0, 100.0, 40.0, 60.0));
    }

    @Test
    void priorRemittanceDoesNotConsumeReplacementCapacity() {
      // live capacity 50 with 30 already remitted: book the full 50, not 20
      assertEquals(50.0, BillableUsageCalculation.computeCoveredDelta(100.0, 50.0, 0.0, 30.0));
    }

    @Test
    void capsAtEffectiveCoverageWhenTotalExceedsFloor() {
      // min(200, 100) - 40 - 0 = 60
      assertEquals(60.0, BillableUsageCalculation.computeCoveredDelta(200.0, 100.0, 40.0, 0.0));
    }
  }

  @Nested
  class Calculate {

    @Test
    void firstTallyUnderContractBooksCoveredOnly() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(80.0),
              100.0,
              new RemittanceTotals(0.0, 0.0, Set.of()),
              Set.of("license-a"),
              REMITTANCE_DATE);

      assertEquals(100.0, calc.getEffectiveCoverage());
      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getBillableValue());
      assertEquals(80.0, calc.getCoveredDelta());
      assertEquals(REMITTANCE_DATE, calc.getRemittanceDate());
    }

    @Test
    void remitsOverageAboveLiveCoverage() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              100.0,
              new RemittanceTotals(0.0, 0.0, Set.of()),
              Set.of("license-a"),
              REMITTANCE_DATE);

      assertEquals(100.0, calc.getEffectiveCoverage());
      assertEquals(30.0, calc.getRemittedValue());
      assertEquals(30.0, calc.getBillableValue());
      assertEquals(100.0, calc.getCoveredDelta());
    }

    @Test
    void doesNotDoubleBillAlreadyRemittedOverage() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              100.0,
              new RemittanceTotals(30.0, 100.0, Set.of("license-a")),
              Set.of("license-a"),
              REMITTANCE_DATE);

      assertEquals(100.0, calc.getEffectiveCoverage());
      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void remitsOnlyNewOverageAbovePriorRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(150.0),
              100.0,
              new RemittanceTotals(30.0, 100.0, Set.of("license-a")),
              Set.of("license-a"),
              REMITTANCE_DATE);

      // usageAbove = 50; already remitted 30 → remit 20
      assertEquals(20.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void replacementLicenseAbsorbsGrowthWithoutRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              40.0,
              new RemittanceTotals(0.0, 80.0, Set.of("license-a")),
              Set.of("license-b"),
              REMITTANCE_DATE);

      assertEquals(120.0, calc.getEffectiveCoverage());
      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(10.0, calc.getCoveredDelta());
    }

    @Test
    void replacementLicenseRemitsOnlyAboveStackedFloor() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(130.0),
              40.0,
              new RemittanceTotals(0.0, 80.0, Set.of("license-a")),
              Set.of("license-b"),
              REMITTANCE_DATE);

      // floor = 80 + 40 = 120 → remit 10; coveredDelta = min(130,120) - 80 = 40
      assertEquals(120.0, calc.getEffectiveCoverage());
      assertEquals(10.0, calc.getRemittedValue());
      assertEquals(40.0, calc.getCoveredDelta());
    }

    @Test
    void sameLicenseCapacityDropRemitsGrowthAboveWatermark() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              40.0,
              new RemittanceTotals(0.0, 80.0, Set.of("license-a")),
              Set.of("license-a"),
              REMITTANCE_DATE);

      assertEquals(80.0, calc.getEffectiveCoverage());
      assertEquals(10.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void emptyLiveCoverageOnReplacementRemitsGrowthAboveWatermark() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(85.0),
              0.0,
              new RemittanceTotals(0.0, 80.0, Set.of("license-a")),
              Set.of("license-b"),
              REMITTANCE_DATE);

      assertEquals(80.0, calc.getEffectiveCoverage());
      assertEquals(5.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void raisingLiveCoverageDoesNotIssueCreditAgainstPriorRemittance() {
      // previously remitted 30 above a small contract; live coverage later rises to 100
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(90.0),
              100.0,
              new RemittanceTotals(30.0, 0.0, Set.of()),
              Set.of("license-a"),
              REMITTANCE_DATE);

      // usageAbove = 0; 0 - 30 → clamped to 0 (no credit)
      assertEquals(100.0, calc.getEffectiveCoverage());
      assertEquals(0.0, calc.getRemittedValue());
      // min(90, 100 + 30) - 0 - 30 = 60 COVERED still bookable beside prior remittance
      assertEquals(60.0, calc.getCoveredDelta());
    }

    @Test
    void nullCurrentTotalYieldsZeros() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(null),
              100.0,
              new RemittanceTotals(0.0, 0.0, Set.of()),
              Set.of("license-a"),
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
              40.0,
              new RemittanceTotals(0.0, 80.0, Set.of("license-a")),
              Set.of("license-b"),
              REMITTANCE_DATE);

      assertEquals(120.0, calc.getEffectiveCoverage());
      assertEquals(0.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
    }

    @Test
    void noContractCoverageRemitsFullMonthToDateMinusPriorRemittance() {
      BillableUsageCalculation calc =
          BillableUsageCalculation.calculate(
              usage(50.0),
              0.0,
              new RemittanceTotals(20.0, 0.0, Set.of()),
              Set.of(),
              REMITTANCE_DATE);

      assertEquals(0.0, calc.getEffectiveCoverage());
      assertEquals(30.0, calc.getRemittedValue());
      assertEquals(0.0, calc.getCoveredDelta());
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
}
