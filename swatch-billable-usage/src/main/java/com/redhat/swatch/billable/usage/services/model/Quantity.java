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

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

/**
 * Value with a unit attached. Encapsulates conversions involving billingFactor. Provides some
 * utility methods for adjustments to amounts.
 *
 * @param <U> Unit of the quantity.
 */
@Getter
@ToString
@EqualsAndHashCode
public class Quantity<U extends Unit> {
  private final double value;
  private final U unit;

  private Quantity(double value, U unit) {
    this.value = value;
    this.unit = unit;
  }

  public Quantity<U> add(Quantity<?> other) {
    var valueInMetricUnits = value / unit.getBillingFactor();
    var otherInMetricUnits = other.getValue() / other.getUnit().getBillingFactor();
    return of(valueInMetricUnits + otherInMetricUnits).to(unit);
  }

  public Quantity<U> subtract(Quantity<?> other) {
    var valueInMetricUnits = value / unit.getBillingFactor();
    var otherInMetricUnits = other.getValue() / other.getUnit().getBillingFactor();
    return of(valueInMetricUnits - otherInMetricUnits).to(unit);
  }

  public Quantity<U> ceil() {
    return new Quantity<>(Math.ceil(value), unit);
  }

  public Quantity<U> positiveOrZero() {
    // less-than-equals used to normalize -0.0 to 0.0
    if (value <= 0) {
      return new Quantity<>(0.0, unit);
    }
    return this;
  }

  public <T extends Unit> Quantity<T> to(T targetUnit) {
    var valueInMetricUnits = value / unit.getBillingFactor();
    var valueInTargetUnits = valueInMetricUnits * targetUnit.getBillingFactor();
    return new Quantity<>(valueInTargetUnits, targetUnit);
  }

  public static Quantity<MetricUnit> of(double value) {
    return new Quantity<>(value, new MetricUnit());
  }

  public static Quantity<BillingUnit> fromContractCoverage(
      BillableUsage referenceUsage, double value) {
    return new Quantity<>(value, new BillingUnit(referenceUsage));
  }

  public static Quantity<RemittanceUnit> fromRemittance(
      BillableUsageRemittanceEntity remittance, Double billingFactor) {
    return new Quantity<>(remittance.getRemittedPendingValue(), new RemittanceUnit(billingFactor));
  }
}
