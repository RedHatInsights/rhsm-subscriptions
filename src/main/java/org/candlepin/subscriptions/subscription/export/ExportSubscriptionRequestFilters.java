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
package org.candlepin.subscriptions.subscription.export;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import java.util.Map;
import java.util.function.BiConsumer;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;

public final class ExportSubscriptionRequestFilters {
  private static final Map<String, BiConsumer<DbReportCriteria.DbReportCriteriaBuilder, String>>
      FILTERS =
          Map.of(
              "product_id",
              ExportSubscriptionRequestFilters::handleProductIdFilter,
              "usage",
              ExportSubscriptionRequestFilters::handleUsageFilter,
              "category",
              ExportSubscriptionRequestFilters::handleCategoryFilter,
              "sla",
              ExportSubscriptionRequestFilters::handleSlaFilter,
              "metric_id",
              ExportSubscriptionRequestFilters::handleMetricIdFilter,
              "billing_provider",
              ExportSubscriptionRequestFilters::handleBillingProviderFilter,
              "billing_account_id",
              ExportSubscriptionRequestFilters::handleBillingAccountIdFilter);

  private ExportSubscriptionRequestFilters() {}

  public static Map<String, BiConsumer<DbReportCriteria.DbReportCriteriaBuilder, String>> get() {
    return FILTERS;
  }

  private static void handleProductIdFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    builder.productId(ProductId.fromString(value).getValue());
  }

  private static void handleUsageFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    Usage usage = Usage.fromString(value);
    if (value.equalsIgnoreCase(usage.getValue())) {
      builder.usage(usage);
    } else {
      throw new IllegalArgumentException(String.format("usage: %s not supported", value));
    }
  }

  private static void handleCategoryFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    builder.hypervisorReportCategory(
        HypervisorReportCategory.mapCategory(ReportCategory.fromString(value)));
  }

  private static void handleSlaFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    ServiceLevel serviceLevel = ServiceLevel.fromString(value);
    if (value.equalsIgnoreCase(serviceLevel.getValue())) {
      builder.serviceLevel(serviceLevel);
    } else {
      throw new IllegalArgumentException(String.format("sla: %s not supported", value));
    }
  }

  private static void handleMetricIdFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    builder.metricId(MetricId.fromString(value).toString());
  }

  private static void handleBillingProviderFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    BillingProvider billingProvider = BillingProvider.fromString(value);
    if (value.equalsIgnoreCase(billingProvider.getValue())) {
      builder.billingProvider(billingProvider);
    } else {
      throw new IllegalArgumentException(
          String.format("billing_provider: %s not supported", value));
    }
  }

  private static void handleBillingAccountIdFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    builder.billingAccountId(value);
  }
}
