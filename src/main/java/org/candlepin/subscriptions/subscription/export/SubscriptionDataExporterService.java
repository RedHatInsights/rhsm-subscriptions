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

import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.billingAccountIdStartsWith;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.billingProviderEquals;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.buildSearchSpecification;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.getMeasurementTypeFromCategory;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.hasCategory;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.hasMetricId;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.productIdEquals;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.slaEquals;
import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.usageEquals;
import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityViewMetric;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.export.DataExporterService;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.SubscriptionsExportJsonMeasurement;
// NOTE(khowell): this couples our export implementation to the v1 REST API
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.candlepin.subscriptions.utilization.api.v1.model.ReportCategory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("capacity-ingress")
@AllArgsConstructor
public class SubscriptionDataExporterService
    implements DataExporterService<SubscriptionCapacityView> {
  static final String SUBSCRIPTIONS_DATA = "subscriptions";
  static final String PRODUCT_ID = "product_id";
  static final String METRIC_ID = "metric_id";
  static final String CATEGORY = "category";
  private final Map<String, Function<String, Specification<SubscriptionCapacityView>>> filters =
      Map.of(
          PRODUCT_ID,
          SubscriptionDataExporterService::handleProductIdFilter,
          "usage",
          SubscriptionDataExporterService::handleUsageFilter,
          CATEGORY,
          this::handleCategoryFilter,
          "sla",
          SubscriptionDataExporterService::handleSlaFilter,
          METRIC_ID,
          SubscriptionDataExporterService::handleMetricIdFilter,
          "billing_provider",
          SubscriptionDataExporterService::handleBillingProviderFilter,
          "billing_account_id",
          SubscriptionDataExporterService::handleBillingAccountIdFilter);

  private final ApiModelMapperV1 mapper;
  private final SubscriptionCapacityViewRepository repository;
  private final SubscriptionJsonDataMapperService jsonDataMapperService;
  private final SubscriptionCsvDataMapperService csvDataMapperService;

  @Override
  public boolean handles(ExportServiceRequest request) {
    return Objects.equals(request.getRequest().getResource(), SUBSCRIPTIONS_DATA);
  }

  @Override
  public Stream<SubscriptionCapacityView> fetchData(ExportServiceRequest request) {
    log.debug("Fetching data for {}", request.getOrgId());
    return repository.streamBy(extractExportFilter(request));
  }

  @Override
  public DataMapperService<SubscriptionCapacityView> getMapper(ExportServiceRequest request) {
    return switch (request.getFormat()) {
      case JSON -> jsonDataMapperService;
      case CSV -> csvDataMapperService;
    };
  }

  private Specification<SubscriptionCapacityView> extractExportFilter(
      ExportServiceRequest request) {
    Specification<SubscriptionCapacityView> criteria = buildSearchSpecification(request.getOrgId());
    if (request.getFilters() != null) {
      var requestedFilters = request.getFilters().entrySet();
      try {
        for (var entry : requestedFilters) {
          var filterHandler = filters.get(entry.getKey().toLowerCase(Locale.ROOT));
          if (filterHandler == null) {
            log.warn("Filter '{}' isn't currently supported. Ignoring.", entry.getKey());
          } else if (entry.getValue() != null) {
            var condition = filterHandler.apply(entry.getValue().toString());
            if (condition != null) {
              criteria = criteria.and(condition);
            }
          }
        }

      } catch (IllegalArgumentException ex) {
        throw new ExportServiceException(
            Response.Status.BAD_REQUEST.getStatusCode(),
            "Wrong filter in export request: " + ex.getMessage());
      }
    }

    return criteria;
  }

  private static Specification<SubscriptionCapacityView> handleProductIdFilter(String value) {
    return productIdEquals(ProductId.fromString(value));
  }

  private static Specification<SubscriptionCapacityView> handleUsageFilter(String value) {
    Usage usage = Usage.fromString(value);
    if (value.equalsIgnoreCase(usage.getValue())) {
      if (!Usage._ANY.equals(usage)) {
        return usageEquals(usage);
      }
    } else {
      throw new IllegalArgumentException(String.format("usage: %s not supported", value));
    }

    return null;
  }

  private static Specification<SubscriptionCapacityView> handleSlaFilter(String value) {
    ServiceLevel serviceLevel = ServiceLevel.fromString(value);
    if (value.equalsIgnoreCase(serviceLevel.getValue())) {
      if (!ServiceLevel._ANY.equals(serviceLevel)) {
        return slaEquals(serviceLevel);
      }
    } else {
      throw new IllegalArgumentException(String.format("sla: %s not supported", value));
    }

    return null;
  }

  private Specification<SubscriptionCapacityView> handleCategoryFilter(String value) {
    return hasCategory(mapper.map(ReportCategory.fromValue(value)));
  }

  private static Specification<SubscriptionCapacityView> handleMetricIdFilter(String value) {
    return hasMetricId(MetricId.fromString(value).toString());
  }

  private static Specification<SubscriptionCapacityView> handleBillingProviderFilter(String value) {
    BillingProvider billingProvider = BillingProvider.fromString(value);
    if (value.equalsIgnoreCase(billingProvider.getValue())) {
      if (!BillingProvider._ANY.equals(billingProvider)) {
        return billingProviderEquals(billingProvider);
      }
    } else {
      throw new IllegalArgumentException(
          String.format("billing_provider: %s not supported", value));
    }

    return null;
  }

  private static Specification<SubscriptionCapacityView> handleBillingAccountIdFilter(
      String value) {
    if (!ANY.equalsIgnoreCase(value)) {
      return billingAccountIdStartsWith(value);
    }

    return null;
  }

  protected static List<SubscriptionsExportJsonMeasurement> groupMetrics(
      ApiModelMapperV1 mapper, SubscriptionCapacityView dataItem, ExportServiceRequest request) {
    Map<MetricKey, SubscriptionsExportJsonMeasurement> metrics = new HashMap<>();

    // metric filters: metric_id and measurement_type
    String filterByMetricId = getMetricIdFilter(request);
    String filterByMeasurementType = getMeasurementTypeFilter(mapper, request);

    for (var metric : dataItem.getMetrics()) {
      if (metric.getMetricId() != null
          && isFilterByMetricId(metric, filterByMetricId)
          && isFilterByMeasurementType(metric, filterByMeasurementType)) {
        var measurement = getOrCreateMeasurement(metrics, metric);
        measurement.setCapacity(measurement.getCapacity() + metric.getCapacity());
      }
    }

    return new ArrayList<>(metrics.values());
  }

  private static boolean isFilterByMetricId(SubscriptionCapacityViewMetric metric, String filter) {
    return filter == null || filter.equalsIgnoreCase(metric.getMetricId());
  }

  private static boolean isFilterByMeasurementType(
      SubscriptionCapacityViewMetric metric, String filter) {
    return filter == null || filter.equalsIgnoreCase(metric.getMeasurementType());
  }

  private static String getMetricIdFilter(ExportServiceRequest request) {
    if (request == null || request.getFilters() == null) {
      return null;
    }

    return Optional.ofNullable(request.getFilters().get(METRIC_ID))
        .map(String.class::cast)
        .orElse(null);
  }

  private static String getMeasurementTypeFilter(
      ApiModelMapperV1 mapper, ExportServiceRequest request) {
    if (request != null
        && request.getFilters() != null
        && request.getFilters().get(CATEGORY) instanceof String value) {
      return getMeasurementTypeFromCategory(mapper.map(ReportCategory.fromValue(value)));
    }

    return null;
  }

  private static SubscriptionsExportJsonMeasurement getOrCreateMeasurement(
      Map<MetricKey, SubscriptionsExportJsonMeasurement> metrics,
      SubscriptionCapacityViewMetric metric) {

    MetricKey key = MetricKey.of(metric);
    return metrics.computeIfAbsent(
        key,
        k -> {
          var m = new SubscriptionsExportJsonMeasurement();
          m.setMetricId(k.metricId);
          m.setMeasurementType(k.measurementType);
          m.setCapacity(0.0);
          return m;
        });
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  private static class MetricKey {
    final String metricId;
    final String measurementType;

    public static MetricKey of(SubscriptionCapacityViewMetric metric) {
      return new MetricKey(metric.getMetricId(), metric.getMeasurementType());
    }
  }
}
