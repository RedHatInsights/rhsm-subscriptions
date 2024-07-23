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
package com.redhat.swatch.billable.usage.data;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

/**
 * A filter used to find {@link BillableUsageRemittanceEntity} objects via the {@link
 * BillableUsageRemittanceRepository}. Any filter value with a null value will not be checked.
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
public class BillableUsageRemittanceFilter {
  private String productId;
  private String orgId;
  private String usage;
  private String sla;
  private String metricId;
  private String billingProvider;
  private String billingAccountId;
  private String hardwareMeasurementType;
  private OffsetDateTime beginning;
  private OffsetDateTime ending;
  private String accumulationPeriod;
  private boolean excludeFailures;

  public static BillableUsageRemittanceFilter fromUsage(BillableUsage usage) {
    return BillableUsageRemittanceFilter.builder()
        .orgId(usage.getOrgId())
        .billingAccountId(usage.getBillingAccountId())
        .billingProvider(usage.getBillingProvider().value())
        .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(usage.getSnapshotDate()))
        .metricId(MetricId.fromString(usage.getMetricId()).getValue())
        .productId(usage.getProductId())
        .sla(usage.getSla().value())
        .usage(usage.getUsage().value())
        .hardwareMeasurementType(usage.getHardwareMeasurementType())
        .build();
  }
}
