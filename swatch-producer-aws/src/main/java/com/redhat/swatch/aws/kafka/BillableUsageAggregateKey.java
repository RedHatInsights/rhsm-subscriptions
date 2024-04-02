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
package com.redhat.swatch.aws.kafka;

import com.redhat.swatch.aws.openapi.model.BillableUsage;
import com.redhat.swatch.aws.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.aws.openapi.model.BillableUsage.SlaEnum;
import com.redhat.swatch.aws.openapi.model.BillableUsage.UsageEnum;
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
        Optional.ofNullable(billableUsage.getSla()).orElse(SlaEnum.EMPTY).value(),
        Optional.ofNullable(billableUsage.getUsage()).orElse(UsageEnum.EMPTY).value(),
        Optional.ofNullable(billableUsage.getBillingProvider())
            .map(BillingProviderEnum::value)
            .orElse(null),
        billableUsage.getBillingAccountId());
  }
}
