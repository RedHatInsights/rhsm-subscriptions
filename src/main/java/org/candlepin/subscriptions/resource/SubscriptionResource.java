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
package org.candlepin.subscriptions.resource;

import java.time.OffsetDateTime;
import javax.validation.constraints.Min;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.candlepin.subscriptions.utilization.api.resources.SubscriptionsApi;
import org.springframework.stereotype.Component;

/** Subscriptions Table API implementation. */
@Component
public class SubscriptionResource implements SubscriptionsApi {

  private final org.candlepin.subscriptions.subscription.SubscriptionTableController
      subscriptionTableController;

  public SubscriptionResource(
      org.candlepin.subscriptions.subscription.SubscriptionTableController
          subscriptionTableController) {
    this.subscriptionTableController = subscriptionTableController;
  }

  @ReportingAccessRequired
  @Override
  public SkuCapacityReport getSkuCapacityReport(
      ProductId productId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ServiceLevelType sla,
      UsageType usage,
      Uom uom,
      SkuCapacityReportSort sort,
      SortDirection dir) {

    return subscriptionTableController.capacityReportBySku(
        productId, offset, limit, sla, usage, uom, sort, dir);
  }
}
