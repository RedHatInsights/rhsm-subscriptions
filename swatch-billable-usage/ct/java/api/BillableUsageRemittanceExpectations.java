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

public final class BillableUsageRemittanceExpectations {

  private BillableUsageRemittanceExpectations() {}

  public static double expectedInitialRemittance(
      double metricUsage, double billingFactor, double contractBillableUnits) {
    double contractValue = contractBillableUnits / billingFactor;
    return Math.ceil(metricUsage * billingFactor) / billingFactor - contractValue;
  }

  /**
   * Remittance after new usage when contracts may have changed mid-month.
   *
   * <p>{@code alreadyRemitted + ceil((totalUsage - alreadyRemitted) × billingFactor) /
   * billingFactor − contractBillableUnits / billingFactor}
   */
  public static double expectedRemittanceAfterUsageIncrease(
      double totalUsage,
      double alreadyRemitted,
      double billingFactor,
      double contractBillableUnits) {
    double contractValue = contractBillableUnits / billingFactor;
    double applicableUsage =
        Math.ceil((totalUsage - alreadyRemitted) * billingFactor) / billingFactor;
    return alreadyRemitted + applicableUsage - contractValue;
  }
}
