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

import static com.redhat.swatch.billable.usage.services.model.Quantity.nonNegativeDifference;

import java.time.OffsetDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

/** Result of monthly billable-usage math for one tally snapshot. */
@Data
@Builder
public class BillableUsageCalculation {
  private double remittedValue;
  private double billableValue;
  private double billingFactor;
  private OffsetDateTime remittanceDate;
  private double effectiveCoverage;
  private double coveredDelta;

  /**
   * @param usage month-to-date tally ({@code currentTotal} + billing-factor config)
   * @param currentCoverage live contract capacity in metric units
   * @param remittanceTotals prior remitted + COVERED totals for the month
   * @param currentLicenseIds licenseIds on the live contracts for this snapshot
   * @param remittanceDate timestamp for new remittance rows
   */
  public static BillableUsageCalculation calculate(
      BillableUsage usage,
      double currentCoverage,
      RemittanceTotals remittanceTotals,
      Set<String> currentLicenseIds,
      OffsetDateTime remittanceDate) {
    double alreadyCovered = remittanceTotals.covered();
    double alreadyRemitted = remittanceTotals.remitted();
    Double currentTotal = usage.getCurrentTotal();

    // How much of currentTotal is prepaid before marketplace remittance.
    double effectiveCoverage =
        computeEffectiveCoverage(
            currentCoverage,
            alreadyCovered,
            currentLicenseIds,
            remittanceTotals.coveredLicenseIds());

    // usageAboveCoverage = max(0, currentTotal - effectiveCoverage)
    double usageAboveCoverage = nonNegativeDifference(currentTotal, effectiveCoverage);

    // remittedValue = ceil(max(0, usageAboveCoverage - alreadyRemitted))
    // Subtract alreadyRemitted to avoid double billing; positiveOrZero blocks mid-month credits.
    var billingUnit = new BillingUnit(usage);
    Quantity<BillingUnit> billableValue =
        Quantity.of(usageAboveCoverage)
            .subtract(Quantity.of(alreadyRemitted))
            .to(billingUnit)
            .ceil()
            .positiveOrZero();

    // coveredDelta = max(0, min(currentTotal, effectiveCoverage + alreadyRemitted) -
    // alreadyCovered - alreadyRemitted)
    // alreadyRemitted shifts the prepaid window forward so prior overage does not consume live
    // capacity; still subtract covered + remitted so those units are not booked again.
    double coveredDelta =
        currentTotal == null
            ? 0.0
            : computeCoveredDelta(currentTotal, effectiveCoverage, alreadyCovered, alreadyRemitted);

    return BillableUsageCalculation.builder()
        .billableValue(billableValue.getValue())
        .remittedValue(billableValue.to(new MetricUnit()).getValue())
        .billingFactor(billingUnit.getBillingFactor())
        .remittanceDate(remittanceDate)
        .effectiveCoverage(effectiveCoverage)
        .coveredDelta(coveredDelta)
        .build();
  }

  static double computeEffectiveCoverage(
      double currentCoverage,
      double alreadyCovered,
      Set<String> currentLicenseIds,
      Set<String> coveredLicenseIds) {
    // Prior COVERED licenses are all gone from live coverage: live capacity is fresh and stacks
    // on the historical COVERED watermark (alreadyCovered + currentCoverage).
    boolean freshCurrentLicenses =
        !coveredLicenseIds.isEmpty()
            && coveredLicenseIds.stream().noneMatch(currentLicenseIds::contains);
    if (freshCurrentLicenses) {
      return alreadyCovered + currentCoverage;
    }

    // At least one COVERED license is still live (or COVERED had no licenseId): use max so the
    // same license cannot grant a second free pool when its capacity shrinks.
    return Math.max(currentCoverage, alreadyCovered);
  }

  static double computeCoveredDelta(
      double currentTotal,
      double effectiveCoverage,
      double alreadyCovered,
      double alreadyRemitted) {
    return nonNegativeDifference(
        Math.min(currentTotal, effectiveCoverage + alreadyRemitted)
            - alreadyCovered
            - alreadyRemitted,
        0.0);
  }
}
