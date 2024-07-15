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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.List;
import java.util.Map;
import java.util.function.ObjDoubleConsumer;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.InstancesExportCsvItem;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class InstancesCsvDataMapperService implements DataMapperService<TallyInstanceView> {

  private final ApiModelMapperV1 mapper;

  public static final Map<MetricId, ObjDoubleConsumer<InstancesExportCsvItem>> METRIC_MAPPER =
      Map.of(
          MetricIdUtils.getCores(), InstancesExportCsvItem::setMeasurementCores,
          MetricIdUtils.getInstanceHours(), InstancesExportCsvItem::setMeasurementInstanceHours,
          MetricIdUtils.getSockets(), InstancesExportCsvItem::setMeasurementSockets,
          MetricIdUtils.getStorageGibibyteMonths(),
              InstancesExportCsvItem::setMeasurementStorageGibibyteMonths,
          MetricIdUtils.getTransferGibibytes(),
              InstancesExportCsvItem::setMeasurementTransferGibibytes,
          MetricIdUtils.getVCpus(), InstancesExportCsvItem::setMeasurementVcpus);

  @Override
  public List<Object> mapDataItem(TallyInstanceView item, ExportServiceRequest request) {
    var instance = new InstancesExportCsvItem();
    instance.setId(item.getId());
    instance.setInstanceId(item.getKey().getInstanceId());
    instance.setDisplayName(item.getDisplayName());
    if (item.getHostBillingProvider() != null) {
      instance.setBillingProvider(item.getHostBillingProvider().getValue());
    }
    instance.setBillingAccountId(item.getHostBillingAccountId());
    var variant = Variant.findByTag(item.getKey().getProductId());
    var metrics = MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).toList();
    for (var metric : metrics) {
      var setter = METRIC_MAPPER.get(metric);
      if (setter != null) {
        setter.accept(instance, item.getMetricValue(metric));
      }
    }

    var category = mapper.measurementTypeToReportCategory(item.getKey().getMeasurementType());
    if (category != null) {
      instance.setCategory(category.toString());
    }
    instance.setLastSeen(item.getLastSeen());
    instance.setHypervisorUuid(item.getHypervisorUuid());
    return List.of(instance);
  }

  @Override
  public Class<TallyInstanceView> getDataClass() {
    return TallyInstanceView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return InstancesExportCsvItem.class;
  }
}
