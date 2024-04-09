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
package org.candlepin.subscriptions.billable.usage;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillableUsageAggregateKey {

  private String orgId;

  private String productId;

  private String metricId;

  private String sla;

  private String usage;

  private String billingProvider;

  private String billingAccountId;

  public BillableUsageAggregateKey(BillableUsage billableUsage) {
    this(
        billableUsage.getOrgId(),
        billableUsage.getProductId(),
        billableUsage.getMetricId(),
        Optional.ofNullable(billableUsage.getSla()).orElse(BillableUsage.Sla.__EMPTY__).value(),
        Optional.ofNullable(billableUsage.getUsage()).orElse(BillableUsage.Usage.__EMPTY__).value(),
        Optional.ofNullable(billableUsage.getBillingProvider())
            .map(BillableUsage.BillingProvider::value)
            .orElse(null),
        billableUsage.getBillingAccountId());
  }
}
