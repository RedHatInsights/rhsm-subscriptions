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
package org.candlepin.subscriptions.tally.export;

import static org.candlepin.subscriptions.db.TallyInstanceViewRepository.buildSearchSpecification;
import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;
import static org.candlepin.subscriptions.resource.api.v1.InstancesResource.getHardwareMeasurementTypesFromCategory;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.TallyInstanceNonPaygViewRepository;
import org.candlepin.subscriptions.db.TallyInstancePaygViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstancesDbReportCriteria;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.export.DataExporterService;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
// NOTE(khowell): this couples our export implementation to the v1 REST API
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Profile("worker")
public class InstancesDataExporterService implements DataExporterService<TallyInstanceView> {

  public static final String INSTANCES_DATA = "instances";
  public static final String PRODUCT_ID = "product_id";
  public static final String BEGINNING = "beginning";
  private static final int BAD_REQUEST = Response.Status.BAD_REQUEST.getStatusCode();

  private static final Map<
          String,
          BiConsumer<TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder, String>>
      FILTERS =
          Map.of(
              PRODUCT_ID,
              InstancesDataExporterService::handleProductIdFilter,
              "usage",
              InstancesDataExporterService::handleUsageFilter,
              "category",
              InstancesDataExporterService::handleCategoryFilter,
              "sla",
              InstancesDataExporterService::handleSlaFilter,
              "metric_id",
              InstancesDataExporterService::handleMetricIdFilter,
              "billing_provider",
              InstancesDataExporterService::handleBillingProviderFilter,
              "billing_account_id",
              InstancesDataExporterService::handleBillingAccountIdFilter,
              BEGINNING,
              InstancesDataExporterService::handleMonthFilter,
              "display_name_contains",
              InstancesDataExporterService::handleDisplayNameContainsFilter);

  private static final List<String> MANDATORY_FILTERS = List.of(PRODUCT_ID);

  private final TallyInstancePaygViewRepository paygViewRepository;
  private final TallyInstanceNonPaygViewRepository nonPaygViewRepository;
  private final InstancesJsonDataMapperService jsonDataMapperService;
  private final InstancesCsvDataMapperService csvDataMapperService;

  @Override
  public boolean handles(ExportServiceRequest request) {
    return Objects.equals(request.getRequest().getResource(), INSTANCES_DATA);
  }

  @Override
  public Stream<TallyInstanceView> fetchData(ExportServiceRequest request) {
    log.debug("Fetching data for {}", request.getOrgId());
    var reportCriteria = extractExportFilter(request);
    boolean isPayg = ProductId.fromString(reportCriteria.getProductId()).isPayg();
    var repository = isPayg ? paygViewRepository : nonPaygViewRepository;
    return repository
        .findBy(buildSearchSpecification(reportCriteria), FluentQuery.FetchableFluentQuery::stream)
        .map(TallyInstanceView.class::cast);
  }

  @Override
  public DataMapperService<TallyInstanceView> getMapper(ExportServiceRequest request) {
    return switch (request.getFormat()) {
      case JSON -> jsonDataMapperService;
      case CSV -> csvDataMapperService;
    };
  }

  private TallyInstancesDbReportCriteria extractExportFilter(ExportServiceRequest request) {
    var report =
        TallyInstancesDbReportCriteria.builder()
            .orgId(request.getOrgId())
            // defaults: it will be overwritten by the provided filters if set
            .sla(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId(ANY)
            .month(InstanceMonthlyTotalKey.formatMonthId(OffsetDateTime.now()));
    var mandatoryFilters = new ArrayList<>(MANDATORY_FILTERS);
    if (request.getFilters() != null) {
      var requestedFilters = request.getFilters().entrySet();
      try {
        for (var entry : requestedFilters) {
          mandatoryFilters.remove(entry.getKey());
          var filterHandler = FILTERS.get(entry.getKey().toLowerCase(Locale.ROOT));
          if (filterHandler == null) {
            log.warn("Filter '{}' isn't currently supported. Ignoring.", entry.getKey());
          } else if (entry.getValue() != null) {
            filterHandler.accept(report, entry.getValue().toString());
          }
        }

      } catch (IllegalArgumentException ex) {
        throw new ExportServiceException(
            BAD_REQUEST, "Wrong filter in export request: " + ex.getMessage());
      }
    }

    if (!mandatoryFilters.isEmpty()) {
      throw new ExportServiceException(
          BAD_REQUEST, "Missing mandatory filters: " + mandatoryFilters);
    }

    // special handling of the month for non payg products
    if (!ProductId.fromString(report.build().getProductId()).isPayg()) {
      report.month(null);
    }

    return report.build();
  }

  private static void handleProductIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.productId(ProductId.fromString(value).toString());
  }

  private static void handleSlaFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    ServiceLevel serviceLevel = ServiceLevel.fromString(value);
    if (value.equalsIgnoreCase(serviceLevel.getValue())) {
      builder.sla(serviceLevel);
    } else {
      throw new IllegalArgumentException(String.format("sla: %s not supported", value));
    }
  }

  private static void handleUsageFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    Usage usage = Usage.fromString(value);
    if (value.equalsIgnoreCase(usage.getValue())) {
      builder.usage(usage);
    } else {
      throw new IllegalArgumentException(String.format("usage: %s not supported", value));
    }
  }

  private static void handleMetricIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.metricId(MetricId.fromString(value));
  }

  private static void handleBillingProviderFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    BillingProvider billingProvider = BillingProvider.fromString(value);
    if (value.equalsIgnoreCase(billingProvider.getValue())) {
      builder.billingProvider(billingProvider);
    } else {
      throw new IllegalArgumentException(
          String.format("billing_provider: %s not supported", value));
    }
  }

  private static void handleBillingAccountIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.billingAccountId(value);
  }

  private static void handleMonthFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    try {
      var date = OffsetDateTime.parse(value);
      builder.month(InstanceMonthlyTotalKey.formatMonthId(date));
    } catch (Exception ex) {
      throw new IllegalArgumentException(String.format("beginning: value %s not supported", value));
    }
  }

  private static void handleCategoryFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.hardwareMeasurementTypes(
        getHardwareMeasurementTypesFromCategory(ReportCategory.fromValue(value)));
  }

  private static void handleDisplayNameContainsFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.displayNameSubstring(value);
  }
}
