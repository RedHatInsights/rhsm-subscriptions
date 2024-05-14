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
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.export.DataExporterService;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("capacity-ingress")
@AllArgsConstructor
public class SubscriptionDataExporterService implements DataExporterService<Subscription> {
  static final String SUBSCRIPTIONS_DATA = "subscriptions";
  static final String PRODUCT_ID = "product_id";
  private static final Map<String, BiConsumer<DbReportCriteria.DbReportCriteriaBuilder, String>>
      FILTERS =
          Map.of(
              PRODUCT_ID,
              SubscriptionDataExporterService::handleProductIdFilter,
              "usage",
              SubscriptionDataExporterService::handleUsageFilter,
              "category",
              SubscriptionDataExporterService::handleCategoryFilter,
              "sla",
              SubscriptionDataExporterService::handleSlaFilter,
              "metric_id",
              SubscriptionDataExporterService::handleMetricIdFilter,
              "billing_provider",
              SubscriptionDataExporterService::handleBillingProviderFilter,
              "billing_account_id",
              SubscriptionDataExporterService::handleBillingAccountIdFilter);

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionJsonDataMapperService jsonDataMapperService;
  private final SubscriptionCsvDataMapperService csvDataMapperService;
  private final ApplicationClock clock;

  @Override
  public boolean handles(ExportServiceRequest request) {
    return Objects.equals(request.getRequest().getResource(), SUBSCRIPTIONS_DATA);
  }

  @Override
  public Stream<Subscription> fetchData(ExportServiceRequest request) {
    log.debug("Fetching data for {}", request.getOrgId());
    var reportCriteria = extractExportFilter(request);
    return subscriptionRepository.streamBy(reportCriteria);
  }

  @Override
  public DataMapperService<Subscription> getMapper(ExportServiceRequest request) {
    return switch (request.getFormat()) {
      case JSON -> jsonDataMapperService;
      case CSV -> csvDataMapperService;
    };
  }

  private DbReportCriteria extractExportFilter(ExportServiceRequest request) {
    var report =
        DbReportCriteria.builder()
            .orgId(request.getOrgId())
            .beginning(clock.now())
            .ending(clock.now());
    if (request.getFilters() != null) {
      var filters = request.getFilters().entrySet();
      try {
        for (var entry : filters) {
          var filterHandler = FILTERS.get(entry.getKey().toLowerCase(Locale.ROOT));
          if (filterHandler == null) {
            log.warn("Filter '{}' isn't currently supported. Ignoring.", entry.getKey());
          } else if (entry.getValue() != null) {
            filterHandler.accept(report, entry.getValue().toString());
          }
        }

      } catch (IllegalArgumentException ex) {
        throw new ExportServiceException(
            Response.Status.BAD_REQUEST.getStatusCode(),
            "Wrong filter in export request: " + ex.getMessage());
      }
    }

    return report.build();
  }

  private static void handleProductIdFilter(
      DbReportCriteria.DbReportCriteriaBuilder builder, String value) {
    var productId = ProductId.fromString(value).getValue();
    if (SubscriptionDefinition.isPrometheusEnabled(productId)) {
      builder.productTag(productId);
    } else {
      builder.productId(productId);
    }
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
