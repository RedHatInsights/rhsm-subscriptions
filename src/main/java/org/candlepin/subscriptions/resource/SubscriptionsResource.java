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

import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.candlepin.subscriptions.subscription.SubscriptionTableController;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.candlepin.subscriptions.utilization.api.resources.SubscriptionsApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Subscriptions Table API implementation. */
@Component
public class SubscriptionsResource implements SubscriptionsApi {

  private final SubscriptionTableController subscriptionTableController;
  private final SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  private final ApplicationClock clock;


  public SubscriptionsResource(
      SubscriptionTableController subscriptionTableController,
      SubscriptionCapacityViewRepository subscriptionCapacityViewRepository,
      ApplicationClock clock,
      ProductProfileRegistry productProfileRegistry) {
    this.subscriptionTableController = subscriptionTableController;
    this.subscriptionCapacityViewRepository = subscriptionCapacityViewRepository;
    this.clock = clock;
  }

  @Transactional
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

    List<SkuCapacity> reportItems =
        new ArrayList<>(
            subscriptionTableController
                .getSkuCapacityReport(
                    productId, beginning, ending, offset, limit, sla, usage, uom, sort, dir)
                .getData());
    sortCapacities(reportItems, sort, dir);
    SkuCapacityReport report = new SkuCapacityReport();
    reportItems = getPage(reportItems, offset, limit);
    report.data(reportItems);

    var meta =
        new HostReportMeta()
            .count(report.getData().size())
            .serviceLevel(sla)
            .usage(usage)
            .uom(uom)
            .product(productId);
    report.setMeta(meta);

    return report;
  }

  private static void sortCapacities(
      List<SkuCapacity> items, SkuCapacityReportSort sort, SortDirection dir) {
    items.sort(
        (left, right) -> {
          var sortField = Optional.ofNullable(sort).orElse(SkuCapacityReportSort.SKU);
          int sortDir = 1;
          if (dir == SortDirection.DESC) {
            sortDir = -1;
          }
          int diff = 0;
          switch (sortField) {
            case SKU:
              diff = left.getSku().compareTo(right.getSku());
              break;
            case SLA:
              diff = left.getServiceLevel().compareTo(right.getServiceLevel());
              break;
            case USAGE:
              diff = left.getUsage().compareTo(right.getUsage());
              break;
            case QUANTITY:
              diff = left.getQuantity().compareTo(right.getQuantity());
              break;
            case NEXT_EVENT:
              diff = left.getUpcomingEventDate().compareTo(right.getUpcomingEventDate());
              break;
            case NEXT_EVENT_TYPE:
              diff = left.getUpcomingEventType().compareTo(right.getUpcomingEventType());
              break;
          }
          // If the two items are sorted by some other field than SKU and are equal, then break the
          // tie by sorting by SKU. No two SKUs in the list are equal.
          if (diff == 0 && sortField != SkuCapacityReportSort.SKU) {
            diff = left.getSku().compareTo(right.getSku());
          }

          return diff * sortDir;
        });
  }

  private static List<SkuCapacity> getPage(List<SkuCapacity> items, Integer offset, Integer limit) {
    // Default to starting from the first item on the list
    int fromIndex = 0;
    if (offset != null) {
      fromIndex = offset;
    }

    // Don't return all objects if the limit results in less items returned, default to all
    int toIndex = items.size();
    if (limit != null && offset + limit < toIndex) {
      toIndex = offset + limit;
    }

    // If the page is beyond the size of the list then return an empty list, otherwise
    // return the sublist.
    if (toIndex <= fromIndex) {
      return Collections.emptyList();
    }
    return items.subList(fromIndex, toIndex);
  }
}
