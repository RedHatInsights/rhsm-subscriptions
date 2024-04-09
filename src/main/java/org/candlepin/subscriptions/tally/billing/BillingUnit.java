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
package org.candlepin.subscriptions.tally.billing;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.candlepin.subscriptions.json.BillableUsage;

/** Metric with the currently configured billingFactor applied. */
@Getter
@ToString
@EqualsAndHashCode
public class BillingUnit implements Unit {
  private final double billingFactor;

  public BillingUnit(BillableUsage usage) {
    var metricOptional =
        Variant.findByTag(usage.getProductId())
            .map(Variant::getSubscription)
            .flatMap(
                subscriptionDefinition ->
                    subscriptionDefinition.getMetric(
                        MetricId.fromString(usage.getMetricId()).getValue()));
    billingFactor =
        metricOptional
            .map(Metric::getBillingFactor)
            .orElse(1.0); // get configured billingFactor from swatch-product-configuration library
  }
}
