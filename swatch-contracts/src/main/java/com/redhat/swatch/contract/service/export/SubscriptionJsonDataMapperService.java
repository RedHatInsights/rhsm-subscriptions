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

import static com.redhat.swatch.contract.service.export.SubscriptionDataExporterService.groupMetrics;

import com.redhat.swatch.contract.model.SubscriptionsExportJsonItem;
import com.redhat.swatch.contract.repository.ServiceLevel;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.Usage;
import com.redhat.swatch.contract.resource.api.v1.ApiModelMapperV1;
import com.redhat.swatch.export.DataMapperService;
import com.redhat.swatch.export.ExportServiceRequest;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;

@ApplicationScoped
@AllArgsConstructor
public class SubscriptionJsonDataMapperService
    implements DataMapperService<SubscriptionCapacityView> {

  private final ApiModelMapperV1 mapper;

  @Override
  public List<Object> mapDataItem(SubscriptionCapacityView dataItem, ExportServiceRequest request) {
    var item = new SubscriptionsExportJsonItem();
    item.setOrgId(dataItem.getOrgId());
    item.setMeasurements(new ArrayList<>());

    // map offering
    item.setSku(dataItem.getSku());
    Optional.ofNullable(dataItem.getUsage()).map(Usage::getValue).ifPresent(item::setUsage);
    Optional.ofNullable(dataItem.getServiceLevel())
        .map(ServiceLevel::getValue)
        .ifPresent(item::setServiceLevel);
    item.setProductName(dataItem.getProductName());
    item.setSubscriptionNumber(dataItem.getSubscriptionNumber());
    item.setQuantity((double) dataItem.getQuantity());

    // aggregate metrics
    item.setMeasurements(groupMetrics(mapper, dataItem, request));

    return List.of(item);
  }

  @Override
  public Class<SubscriptionCapacityView> getDataClass() {
    return SubscriptionCapacityView.class;
  }

  @Override
  public Class<?> getExportItemClass() {
    return SubscriptionsExportJsonItem.class;
  }
}
