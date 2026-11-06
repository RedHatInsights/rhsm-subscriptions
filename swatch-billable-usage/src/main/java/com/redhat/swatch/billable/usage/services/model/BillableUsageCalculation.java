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

import static com.redhat.swatch.billable.usage.services.BillableUsageService.NO_LICENSE_ID;
import static com.redhat.swatch.billable.usage.services.model.Quantity.nonNegativeDifference;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
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

  /** New COVERED amounts to book this tally, keyed by licenseId in allocation order. */
  @Builder.Default
  private SequencedMap<String, Double> coveredDeltaByLicense =
      Collections.unmodifiableSequencedMap(new LinkedHashMap<>());

  /** Total COVERED to book this tally (sum of {@link #coveredDeltaByLicense}). */
  public double getCoveredDelta() {
    return sum(coveredDeltaByLicense);
  }

  /**
   * Four-step monthly calculation:
   *
   * <ol>
   *   <li>Effective delta — {@code currentTotal - alreadyRemitted - alreadyCovered}.
   *   <li>Remaining license pool — live capacity minus already COVERED on each live license.
   *   <li>Covered allocation — apply the effective delta against remaining room (allocation order).
   *   <li>Remittance — whatever of the effective delta is not covered by a license.
   * </ol>
   *
   * <p>Expired COVERED stays in {@code alreadyCovered} (step 1) and is never re-opened as live
   * room, so it acts as a prepaid watermark without a separate floor formula.
   */
  public static BillableUsageCalculation calculate(
      BillableUsage usage,
      ContractCoverage contractCoverage,
      RemittanceTotals remittanceTotals,
      OffsetDateTime remittanceDate) {
    Double currentTotal = usage.getCurrentTotal();
    if (currentTotal == null) {
      return empty(usage, remittanceDate);
    }

    // 1. Usage not yet booked as remitted or covered.
    double effectiveDelta =
        nonNegativeDifference(
            currentTotal, remittanceTotals.remitted() + remittanceTotals.covered());

    // 2. Remaining capacity room per live license (metric units, allocation order).
    SequencedMap<String, Double> remainingCapacityByLicense =
        remainingLicenseCapacity(usage, contractCoverage, remittanceTotals.coveredByLicense());

    // 3. How much of the effective delta each live license can still cover.
    SequencedMap<String, Double> coveredDeltaByLicense =
        allocateCovered(effectiveDelta, remainingCapacityByLicense);

    // 4. Anything left after license coverage must be remitted.
    double toRemit = nonNegativeDifference(effectiveDelta, sum(coveredDeltaByLicense));

    var billingUnit = new BillingUnit(usage);
    Quantity<BillingUnit> billableValue =
        Quantity.of(toRemit).to(billingUnit).ceil().positiveOrZero();

    return BillableUsageCalculation.builder()
        .billableValue(billableValue.getValue())
        .remittedValue(billableValue.to(new MetricUnit()).getValue())
        .billingFactor(billingUnit.getBillingFactor())
        .remittanceDate(remittanceDate)
        .coveredDeltaByLicense(coveredDeltaByLicense)
        .build();
  }

  static SequencedMap<String, Double> remainingLicenseCapacity(
      BillableUsage usage,
      ContractCoverage contractCoverage,
      Map<String, Double> alreadyCoveredByLicense) {
    SequencedMap<String, Double> remaining = new LinkedHashMap<>();
    contractCoverage
        .getCapacityByLicense()
        .forEach((licenseId, billableCapacity) -> {
          double metricCapacity =
              Quantity.fromValue(usage, nullToZero(billableCapacity)).toMetricUnits();
          String key = licenseId == null || licenseId.isBlank() ? NO_LICENSE_ID : licenseId;
          double already = nullToZero(alreadyCoveredByLicense.get(key));
          remaining.put(key, Math.max(0.0, metricCapacity - already));
        });
    return Collections.unmodifiableSequencedMap(remaining);
  }

  static SequencedMap<String, Double> allocateCovered(
      double effectiveDelta, SequencedMap<String, Double> remainingCapacityByLicense) {
    SequencedMap<String, Double> covered = new LinkedHashMap<>();
    double remaining = effectiveDelta;
    for (Map.Entry<String, Double> entry : remainingCapacityByLicense.entrySet()) {
      if (remaining <= 0) {
        break;
      }
      double take = Math.min(remaining, nullToZero(entry.getValue()));
      if (take > 0) {
        covered.put(entry.getKey(), take);
        remaining -= take;
      }
    }
    return Collections.unmodifiableSequencedMap(covered);
  }

  private static BillableUsageCalculation empty(
      BillableUsage usage, OffsetDateTime remittanceDate) {
    var billingUnit = new BillingUnit(usage);
    return BillableUsageCalculation.builder()
        .billableValue(0.0)
        .remittedValue(0.0)
        .billingFactor(billingUnit.getBillingFactor())
        .remittanceDate(remittanceDate)
        .build();
  }

  private static double sum(Map<String, Double> values) {
    return values.values().stream().mapToDouble(BillableUsageCalculation::nullToZero).sum();
  }

  private static double nullToZero(Double value) {
    return value == null ? 0.0 : value;
  }
}
