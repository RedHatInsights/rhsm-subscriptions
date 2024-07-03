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

import static org.candlepin.subscriptions.resource.api.v1.InstancesResource.getCategoryByMeasurementType;
import static org.candlepin.subscriptions.resource.api.v1.InstancesResource.getCloudProviderByMeasurementType;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.export.DataMapperService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.InstancesExportJsonGuest;
import org.candlepin.subscriptions.json.InstancesExportJsonItem;
import org.candlepin.subscriptions.json.InstancesExportJsonMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class InstancesJsonDataMapperService implements DataMapperService<TallyInstanceView> {

  private static final int MAX_GUESTS_PER_QUERY = 20;

  private final HostRepository hostRepository;

  @Override
  public List<Object> mapDataItem(TallyInstanceView item, ExportServiceRequest request) {
    var instance = new InstancesExportJsonItem();
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

    var cloudProvider = getCloudProviderByMeasurementType(item.getKey().getMeasurementType());
    if (cloudProvider != null) {
      instance.setCloudProvider(cloudProvider.toString());
    }

    instance.setBillingAccountId(item.getHostBillingAccountId());
    instance.setMeasurements(new ArrayList<>());
    var variant = Variant.findByTag(item.getKey().getProductId());
    var metrics = MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).toList();
    for (var metric : metrics) {
      instance
          .getMeasurements()
          .add(
              toInstanceExportMetric(
                  metric, Optional.ofNullable(item.getMetricValue(metric)).orElse(0.0)));
    }

    instance.setLastSeen(item.getLastSeen());
    instance.setNumberOfGuests(item.getNumOfGuests());
    instance.setSubscriptionManagerId(item.getSubscriptionManagerId());
    instance.setInventoryId(item.getInventoryId());
    if (item.getNumOfGuests() > 0) {
      var guestsInInstance =
          getGuestHostsByHypervisorInstanceId(item, PageRequest.ofSize(MAX_GUESTS_PER_QUERY));
      Set<InstancesExportJsonGuest> guests =
          new HashSet<>(mapHostGuests(guestsInInstance.getContent()));
      while (guestsInInstance.hasNext()) {
        guestsInInstance =
            getGuestHostsByHypervisorInstanceId(item, guestsInInstance.nextPageable());
        guests.addAll(mapHostGuests(guestsInInstance.getContent()));
      }

      instance.setGuests(new ArrayList<>(guests));
    }
    return List.of(instance);
  }

  @Override
  public Class<TallyInstanceView> getDataClass() {
    return TallyInstanceView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return InstancesExportJsonItem.class;
  }

  private Page<Host> getGuestHostsByHypervisorInstanceId(
      TallyInstanceView item, Pageable pageRequest) {
    return hostRepository.getGuestHostsByHypervisorInstanceId(
        item.getOrgId(), item.getKey().getInstanceId(), pageRequest);
  }

  private List<InstancesExportJsonGuest> mapHostGuests(List<Host> guests) {
    return guests.stream()
        .map(
            guest ->
                new InstancesExportJsonGuest()
                    .withDisplayName(guest.getDisplayName())
                    .withHardwareType(
                        guest.getHardwareType() == null ? null : guest.getHardwareType().toString())
                    .withInsightsId(guest.getInsightsId())
                    .withInventoryId(guest.getInventoryId())
                    .withSubscriptionManagerId(guest.getSubscriptionManagerId())
                    .withLastSeen(guest.getLastSeen())
                    .withCloudProvider(guest.getCloudProvider())
                    .withIsUnmappedGuest(guest.isUnmappedGuest())
                    .withIsHypervisor(guest.isHypervisor()))
        .toList();
  }

  private static InstancesExportJsonMetric toInstanceExportMetric(MetricId metric, double value) {
    return new InstancesExportJsonMetric().withMetricId(metric.toString()).withValue(value);
  }
}
