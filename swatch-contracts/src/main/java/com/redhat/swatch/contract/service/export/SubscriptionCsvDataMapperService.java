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
package com.redhat.swatch.contract.service.export;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.model.SubscriptionsExportCsvItem;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.contract.resource.api.v1.ApiModelMapperV1;
import com.redhat.swatch.export.DataMapperService;
import com.redhat.swatch.export.ExportServiceRequest;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;

@ApplicationScoped
@AllArgsConstructor
public class SubscriptionCsvDataMapperService
    implements DataMapperService<SubscriptionCapacityView> {

  private final ApiModelMapperV1 mapper;

  @Override
  public List<Object> mapDataItem(SubscriptionCapacityView dataItem, ExportServiceRequest request) {
    if (dataItem.getMetrics().isEmpty()) {
      return List.of();
    }

    return SubscriptionDataExporterService.groupMetrics(mapper, dataItem, request).stream()
        .map(
            m -> {
              var item = new SubscriptionsExportCsvItem();
              item.setSubscriptionId(dataItem.getSubscriptionId());
              item.setSubscriptionNumber(dataItem.getSubscriptionNumber());
              item.setBegin(dataItem.getStartDate());
              item.setEnd(dataItem.getEndDate());
              item.setQuantity((double) dataItem.getQuantity());
              item.setMetricId(m.getMetricId());
              if (SubscriptionCapacityViewRepository.PHYSICAL.equals(m.getMeasurementType())) {
                item.setHypervisorCapacity(m.getCapacity());
              } else {
                item.setCapacity(m.getCapacity());
              }

              // map offering
              item.setSku(dataItem.getSku());
              Optional.ofNullable(dataItem.getUsage())
                  .map(Usage::getValue)
                  .ifPresent(item::setUsage);
              Optional.ofNullable(dataItem.getServiceLevel())
                  .map(ServiceLevel::getValue)
                  .ifPresent(item::setServiceLevel);
              item.setProductName(dataItem.getProductName());
              return (Object) item;
            })
        .toList();
  }

  @Override
  public Class<SubscriptionCapacityView> getDataClass() {
    return SubscriptionCapacityView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return SubscriptionsExportCsvItem.class;
  }
}
