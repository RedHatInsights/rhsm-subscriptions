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
package org.candlepin.subscriptions.resource.api.v2;

import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.v2.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v2.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v2.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReport;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReportSort;
import org.candlepin.subscriptions.utilization.api.v2.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.v2.model.UsageType;
import org.candlepin.subscriptions.utilization.api.v2.resources.SubscriptionsApi;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Component
@AllArgsConstructor
public class SubscriptionResourceV2 implements SubscriptionsApi {
  private final SubscriptionTableController subscriptionTableController;

  @ReportingAccessRequired
  @Override
  public SkuCapacityReport getSkuCapacityReport(
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usage,
      BillingProviderType billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String metricId,
      SkuCapacityReportSort sort,
      SortDirection dir) {
    return subscriptionTableController.capacityReportBySku(
        productId,
        offset,
        limit,
        category,
        sla,
        usage,
        billingProvider,
        billingAccountId,
        metricId,
        sort,
        dir);
  }
}
