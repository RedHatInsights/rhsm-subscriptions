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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjDoubleConsumer;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.InstancesExportCsvItem;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class InstancesCsvDataMapperService implements DataMapperService<TallyInstanceView> {

  private static final String RHEL_FOR_X86 = "RHEL for x86";
  private static final int MAX_GUESTS_PER_QUERY = 20;

  private final ApiModelMapperV1 mapper;
  private final HostRepository hostRepository;

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
    List<Object> dataItems = new ArrayList<>();
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
    instance.setNumberOfGuests(item.getNumOfGuests());
    instance.setSubscriptionManagerId(item.getSubscriptionManagerId());
    instance.setInventoryId(item.getInventoryId());
    dataItems.add(instance);

    if (needsToExportGuests(item)) {
      dataItems.addAll(mapGuests(item));
    }

    return dataItems;
  }

  @Override
  public Class<TallyInstanceView> getDataClass() {
    return TallyInstanceView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return InstancesExportCsvItem.class;
  }

  private boolean needsToExportGuests(TallyInstanceView item) {
    return item.getNumOfGuests() > 0
        && RHEL_FOR_X86.equals(item.getKey().getProductId())
        && HardwareMeasurementType.HYPERVISOR.equals(item.getKey().getMeasurementType());
  }

  private List<InstancesExportCsvItem> mapGuests(TallyInstanceView item) {
    var guestsInInstance =
        getGuestHostsByHypervisorInstanceId(item, PageRequest.ofSize(MAX_GUESTS_PER_QUERY));
    Set<InstancesExportCsvItem> guests =
        new HashSet<>(toInstancesExportCsvItem(guestsInInstance.getContent()));
    while (guestsInInstance.hasNext()) {
      guestsInInstance = getGuestHostsByHypervisorInstanceId(item, guestsInInstance.nextPageable());
      guests.addAll(toInstancesExportCsvItem(guestsInInstance.getContent()));
    }

    return new ArrayList<>(guests);
  }

  private List<InstancesExportCsvItem> toInstancesExportCsvItem(List<Host> guests) {
    return guests.stream()
        .map(
            guest ->
                new InstancesExportCsvItem()
                    .withId(guest.getId().toString())
                    .withDisplayName(guest.getDisplayName())
                    .withInventoryId(guest.getInventoryId())
                    .withHardwareType(
                        guest.getHardwareType() == null ? null : guest.getHardwareType().toString())
                    .withInstanceId(guest.getInstanceId())
                    .withSubscriptionManagerId(guest.getSubscriptionManagerId())
                    .withHypervisorUuid(guest.getHypervisorUuid())
                    .withLastSeen(guest.getLastSeen()))
        .toList();
  }

  private Page<Host> getGuestHostsByHypervisorInstanceId(
      TallyInstanceView item, Pageable pageRequest) {
    return hostRepository.getGuestHostsByHypervisorInstanceId(
        item.getOrgId(), item.getKey().getInstanceId(), pageRequest);
  }
}
