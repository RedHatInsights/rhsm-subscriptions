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

import static org.candlepin.subscriptions.resource.InstancesResource.getCategoryByMeasurementType;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.InstancesExportCsvItem;
import org.candlepin.subscriptions.json.InstancesExportJsonMetric;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class InstancesCsvDataMapperService implements DataMapperService<TallyInstanceView> {

  @Override
  public List<Object> mapDataItem(TallyInstanceView item, ExportServiceRequest request) {
    var instance = new InstancesExportCsvItem();
    instance.setId(item.getId());
    instance.setInstanceId(item.getKey().getInstanceId());
    instance.setDisplayName(item.getDisplayName());
    if (item.getHostBillingProvider() != null) {
      instance.setBillingProvider(item.getHostBillingProvider().getValue());
    }
    var category = getCategoryByMeasurementType(item.getKey().getMeasurementType());
    if (category != null) {
      instance.setCategory(category.toString());
    }

    instance.setBillingAccountId(item.getHostBillingAccountId());
    var variant = Variant.findByTag(item.getKey().getProductId());
    var metrics = MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).toList();
    for (var metric : metrics) {
      poplulateMetric(
          instance,
          toInstanceExportMetric(
              metric, Optional.ofNullable(item.getMetricValue(metric)).orElse(0.0)));
    }
    instance.setLastSeen(item.getLastSeen());
    return List.of(instance);
  }

  private static void poplulateMetric(
      InstancesExportCsvItem instance, InstancesExportJsonMetric metric) {
    switch (metric.getMetricId().toLowerCase().replace('-', '_')) {
      case ("cores"):
        instance.setMeasurementCores(metric.getValue());
        break;
      case ("instances"):
        instance.setMeasurementInstances(metric.getValue());
        break;
      case ("instance_hours"):
        instance.setMeasurementInstanceHours(metric.getValue());
        break;
      case ("sockets"):
        instance.setMeasurementSockets(metric.getValue());
        break;
      case ("storage_gibibytes"):
        instance.setMeasurementStorageGibibytes(metric.getValue());
        break;
      case ("storage_gibibyte_months"):
        instance.setMeasurementStorageGibibyteMonths(metric.getValue());
        break;
      case ("transfer_gibibytes"):
        instance.setMeasurementTransferGibibytes(metric.getValue());
        break;
      case ("vcpus"):
        instance.setMeasurementVcpus(metric.getValue());
        break;
      default:
    }
  }

  @Override
  public Class<TallyInstanceView> getDataClass() {
    return TallyInstanceView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return InstancesExportCsvItem.class;
  }

  private static InstancesExportJsonMetric toInstanceExportMetric(MetricId metric, double value) {
    return new InstancesExportJsonMetric().withMetricId(metric.toString()).withValue(value);
  }
}
